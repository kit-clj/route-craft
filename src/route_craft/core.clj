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

(defn get-by-id-handler
  [{:keys [id-fn id-key req-db-conn table-name]
    :or   {id-fn       path-id-fn
           id-key      :id
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [id (id-fn request)]
    (if-let [resp (sql/get-by-id (req-db-conn request) (keyword table-name) id id-key)]
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

(defn update-by-id-handler
  [{:keys [id-fn id-key req-body req-db-conn table-name]
    :or   {id-fn       path-id-fn
           id-key      :id
           req-body    req-body
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [body (req-body request)
        id   (id-fn request)]
    (http-response/ok (sql/update! (req-db-conn request)
                                   (keyword table-name)
                                   body
                                   {id-key id}))))

(defn delete-by-id-handler
  [{:keys [id-fn id-key req-db-conn table-name]
    :or   {id-fn       path-id-fn
           id-key      :id
           req-db-conn req-db-conn}
    :as   opts}
   request]
  (let [id (id-fn request)]
    (sql/delete! (req-db-conn request)
                 (keyword table-name)
                 {id-key id})
    (http-response/ok [id-key id])))

;; ===================ROUTING===================

;; generate crud routes using default handlers
;; permit overriding handlers where specifed in config
;; malli schema for swaggerui

;; TODO: extend in opts
;; TODO: handle arrays
(def malli-type-mapping
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

(def default-methods [:get :post :put :delete])

(def default-id-key :id)

(defmulti generate-method-handler (fn [_opts method] method))

(defn id-type
  [{:keys [table id-key]}]
  (->> (get-in table [:columns id-key :column-type])
       (get malli-type-mapping)))

(defn malli-column-key
  ([column-key column-meta] (malli-column-key column-key column-meta {:force-optional? false}))
  ([column-key {:keys [column-type nullable?]} {:keys [force-optional?]}]
   (let [malli-type (get malli-type-mapping column-type)]
     (cond
       nullable?
       [:maybe [column-key {:optional true} malli-type]]

       force-optional?
       [column-key {:optional true} malli-type]

       :else
       [column-key malli-type]))))

(defn table->malli-map
  ([table] (table->malli-map table false {}))
  ([table ignore-primary-key?] (table->malli-map table ignore-primary-key? {:force-optional? false}))
  ([{:keys [columns column-order]} ignore-primary-key? column-key-opts]
   (reduce
     (fn [out column-key]
       (let [{:keys [primary-key?] :as column} (get columns column-key)]
         (if (and primary-key? ignore-primary-key?)
           out
           (conj out (malli-column-key column-key column column-key-opts)))))
     [:map]
     column-order)))

;; TODO: handle failed mapping finds more gracefully
(defmethod generate-method-handler :get
  [{:keys [table id-key] :as opts} _]
  [(str "/" id-key)
   {:get        (partial get-by-id-handler opts)
    :parameters {:path [:map [id-key (id-type opts)]]}
    :responses  {200 {:body (table->malli-map table)}}}])

(defmethod generate-method-handler :post
  [{:keys [table] :as opts} _]
  ["" {:post       (partial create-handler opts)
       :parameters {:body (table->malli-map table true)}
       :responses  {200 {:body (table->malli-map table)}}}])

(defmethod generate-method-handler :put
  [{:keys [table id-key] :as opts} _]
  ["/:id" {:put        (partial update-by-id-handler opts)
           :parameters {:path [:map [id-key (id-type opts)]]
                        :body (table->malli-map table true {:force-optional? true})}
           :responses  {200 {:body (table->malli-map table)}}}])

(defmethod generate-method-handler :delete
  [{:keys [id-key] :as opts} _]
  (let [malli-id-def [id-key (id-type opts)]]
    ["/:id" {:delete     (partial delete-by-id-handler opts)
             :parameters {:path [:map malli-id-def]}
             :responses  {200 {:body [:map malli-id-def]}}}]))

(defmethod generate-method-handler :default
  [_ method]
  (throw (ex-info "Unsupported method handler" {:type   ::unsupported-method-handler
                                                :method method})))

(defn routes-from-dbxray
  [{:keys [table-definitions]} {:keys [table-order tables]}]
  (sequence
    (comp (map
            (fn [table]
              (let [table-opts (get table-definitions table)]
                (when-not (:ignore? table-opts)
                  [(str "/" (name table))
                   (mapv (partial generate-method-handler {:table-name table
                                                           :table      (get tables table)
                                                           :id-key     (or (:id-key table-opts) default-id-key)})
                         (get-in table-definitions [table :methods] default-methods))]))))
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

;; table-definitions map
;; if a table is not specified, it is assumed that all CRUD is permitted (? maybe dangerous)
{:flyway_schema_history {:ignore? true}
 :locales               {:methods [:get]}
 }

(defn generate-reitit-crud-routes
  [{:keys [table-definitions
           role-parser-fn
           db-conn
           ]
    :as   opts}]
  (try
    (let [db-xray (dbx/xray (jdbc/get-connection db-conn))]
      )
    (catch Exception e
      (log/error e "Failed to create reitit routes"))))

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
    (migratus/migrate migratus-config)))