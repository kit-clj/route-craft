(ns route-craft.core
  (:require
    [clojure.tools.logging :as log]
    [donut.dbxray :as dbx]
    [next.jdbc :as jdbc]
    [next.jdbc.sql :as sql]
    [ring.util.http-response :as http-response]
    [kit.edge.db.postgres]))

;; MVP

;; ===================HANDLERS===================

;; handle the following RESTful API calls:
;; - GET - get by id
;; - POST - create
;; - PUT - update by id
;; - DELETE - delete by id

(defn query-id-fn
  [request]
  (get-in request [:parameters :query :id]))

(defn path-id-fn
  [request]
  (get-in request [:parameters :path :id]))

(defn req-body
  [request]
  (get-in request [:parameters :body]))

(defn req-db-conn
  [request]
  (get-in request [:system/context :db-conn]))

(defn get-by-pk-handler
  [{:keys [id-fn pk-key req-db-conn table-name]
    :or   {id-fn       path-id-fn
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [id (id-fn request)]
    (if-let [resp (sql/get-by-id (req-db-conn request) (keyword table-name) id pk-key)]
      (http-response/ok resp)
      (http-response/not-found))))

(defn create-handler
  [{:keys [req-body req-db-conn table-name]
    :or   {req-db-conn req-db-conn
           req-body    req-body}
    :as   opts}
   request]
  (let [body (req-body request)]
    (http-response/ok (sql/insert! (req-db-conn request)
                                   (keyword table-name)
                                   body))))

(defn update-by-pk-handler
  [{:keys [id-fn pk-key req-body req-db-conn table-name]
    :or   {id-fn       path-id-fn
           req-body    req-body
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [body (req-body request)
        id   (id-fn request)]
    (http-response/ok (sql/update! (req-db-conn request)
                                   (keyword table-name)
                                   body
                                   {pk-key id}))))

(defn delete-by-pk-handler
  [{:keys [id-fn pk-key req-db-conn table-name]
    :or   {id-fn       path-id-fn
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [id (id-fn request)]
    (sql/delete! (req-db-conn request)
                 (keyword table-name)
                 {pk-key id})
    (http-response/ok [pk-key id])))

;; ===================ROUTING===================

;; generate crud routes using default handlers
;; permit overriding handlers where specifed in config
;; malli schema for swaggerui

;; TODO: handle arrays
(def base-malli-type-mappings
  {:integer     :int
   :bool        :boolean
   :text        :string
   :varchar     :string
   :uuid        :uuid
   :bigint      :int
   :timestamptz :time/instant
   :timestamp   :time/instant
   :date        :time/local-date
   :jsonb       :any
   :hstore      :any
   :oid         :string})

(defmulti generate-handler (fn [_opts handler] handler))

(defn id-type
  [{:keys [malli-type-mappings table pk-key]}]
  (->> (get-in table [:columns pk-key :column-type])
       (get malli-type-mappings)))

(defn malli-column-key
  ([malli-type-mappings column-key column-meta]
   (malli-column-key malli-type-mappings column-key column-meta {:force-optional? false}))
  ([malli-type-mappings column-key {:keys [column-type nullable?]} {:keys [force-optional?]}]
   (let [malli-type (get malli-type-mappings column-type)]
     (cond
       nullable?
       [:maybe [column-key {:optional true} malli-type]]

       force-optional?
       [column-key {:optional true} malli-type]

       :else
       [column-key malli-type]))))

(defn table->malli-map
  ([opts table] (table->malli-map opts table false {}))
  ([opts table ignore-primary-key?] (table->malli-map opts table ignore-primary-key? {:force-optional? false}))
  ([{:keys [malli-type-mappings]} {:keys [columns column-order]} ignore-primary-key? column-key-opts]
   (reduce
     (fn [out column-key]
       (let [{:keys [primary-key?] :as column} (get columns column-key)]
         (if (and primary-key? ignore-primary-key?)
           out
           (conj out (malli-column-key malli-type-mappings column-key column column-key-opts)))))
     [:map]
     column-order)))

;; TODO: handle failed mapping finds more gracefully
(defmethod generate-handler :get-by-pk
  [{:keys [table pk-key] :as opts} _]
  {:handler    (partial get-by-pk-handler opts)
   :parameters {:path [:map [pk-key (id-type opts)]]}
   :responses  {200 {:body (table->malli-map opts table)}}})

(defmethod generate-handler :insert-one
  [{:keys [table] :as opts} _]
  {:handler    (partial create-handler opts)
   :parameters {:body (table->malli-map table true)}
   :responses  {200 {:body (table->malli-map opts table)}}})

(defmethod generate-handler :update-by-pk
  [{:keys [table pk-key] :as opts} _]
  {:handler    (partial update-by-pk-handler opts)
   :parameters {:path [:map [pk-key (id-type opts)]]
                :body (table->malli-map table true {:force-optional? true})}
   :responses  {200 {:body (table->malli-map opts table)}}})

(defmethod generate-handler :delete-by-pk
  [{:keys [pk-key] :as opts} _]
  (let [malli-id-def [pk-key (id-type opts)]]
    {:handler    (partial delete-by-pk-handler opts)
     :parameters {:path [:map malli-id-def]}
     :responses  {200 {:body [:map malli-id-def]}}}))

(defmethod generate-handler :default
  [_ handler]
  (throw (ex-info "Unsupported handler" {:type    ::unsupported-handler
                                         :handler handler})))

(defn get-pk-from-table
  [{:keys [columns]}]
  (let [pk-cols (reduce-kv (fn [out k {:keys [primary-key?]}]
                             (if primary-key?
                               (conj out k)
                               out))
                           #{}
                           columns)]
    (if (= 1 (count pk-cols))
      (first pk-cols)
      (throw (ex-info "Unexpected primary key count, please define manually" {:count (count pk-cols)
                                                                              :type  ::unexpected-pk-count})))))

(defn table->reitit-routes
  [{:keys [table-definitions default-handlers throw-on-failure? malli-type-mappings]
    :or   {default-handlers  []
           throw-on-failure? true}}
   {:keys [tables]}
   table]
  (let [table-opts (get table-definitions table)]
    (when-not (:ignore? table-opts)
      (let [handlers (get-in table-definitions [table :handlers] default-handlers)]
        (when (seq handlers)
          (try (let [dbtable             (get tables table)
                     pk-key              (or (:pk-key table-opts) (get-pk-from-table dbtable))
                     pk-key-path         (str "/" pk-key)
                     generate-handler-fn (partial generate-handler {:table-name          table
                                                                    :table               dbtable
                                                                    :pk-key              pk-key
                                                                    :malli-type-mappings malli-type-mappings})]
                 (->> (reduce
                        (fn [out handler]
                          (let [path (case handler
                                       :insert-one ["" :post]
                                       :get-by-pk [pk-key-path :get]
                                       :update-by-pk [pk-key-path :put]
                                       :delete-by-pk [pk-key-path :delete]
                                       (throw (ex-info "Unsupported handler" {:type    ::unsupported-handler
                                                                              :handler handler})))]
                            (assoc-in out path (generate-handler-fn handler))))
                        {}
                        handlers)
                      (vec)
                      (cons (str "/" (name table)))
                      (vec)))
               (catch Exception e
                 (log/trace e "Route generation exception")
                 (log/warn "Failed to generate routes for table" table)
                 (when throw-on-failure?
                   (throw e)))))))))

(defn routes-from-dbxray
  [opts {:keys [table-order] :as db-xray}]
  (into []
        (comp (map (partial table->reitit-routes opts db-xray))
              (filter identity))
        table-order))

;; ===================PERMISSIONS===================

;; Let user choose between RLS and app permissions
;; Either way need way to fetch user id and role from request

;; ===================CONFIGURATION===================

;; specify which routes have which CRUD capabilities
;; ignore certain tables
;; permission definitions

;; ignore certain columns / hide columns ?
;; handling defaults

(defn generate-reitit-crud-routes
  [{:keys [table-definitions
           role-parser-fn
           malli-type-mappings
           default-handlers
           throw-on-failure?
           db-conn]
    :as   opts}]
  (try
    (let [db-xray (dbx/xray db-conn)]
      (routes-from-dbxray (update opts :malli-type-mappings #(merge base-malli-type-mappings %)) db-xray))
    (catch Exception e
      (log/error e "Failed to create reitit routes")
      (when throw-on-failure?
        (throw e)))))

;; BEYOND MVP
;; - update by query
;; - upsert
;; - insert multi
;; - delete by query
;; - generic query
;; - query only specific columns
;; - resolve fks


(comment
  (def ctx
    {:datasource (jdbc/get-datasource {:jdbcUrl "jdbc:postgresql://127.0.0.1:5432/rc?user=rc&password=rc"})})

  (let [migratus-config {:migration-dir "test/resources/migrations"
                         :db            ctx
                         :store         :database}]
    (migratus/migrate migratus-config))

  (ring/router
    (generate-reitit-crud-routes
      {:db-conn (jdbc/get-connection (:datasource ctx))})))