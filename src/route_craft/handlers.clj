(ns route-craft.handlers
  (:require [next.jdbc.sql :as sql]
            [ring.util.http-response :as http-response]))

;; ===================HANDLERS===================

;; handle the following RESTful API calls:
;; - GET - get by id
;; - POST - create
;; - PUT - update by id
;; - DELETE - delete by id

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

(defmulti generate-handler (fn [_opts handler] handler))

(defn id-type
  [{:keys [malli-type-mappings table pk-key]}]
  (->> (get-in table [:columns pk-key :column-type])
       (get malli-type-mappings)))

(defn malli-column-key
  ([malli-type-mappings column-key column-meta]
   (malli-column-key malli-type-mappings column-key column-meta {:force-optional? false}))
  ([malli-type-mappings column-key {:keys [column-type nullable? array?]} {:keys [force-optional?]}]
   (let [base-malli-type (get malli-type-mappings column-type)
         malli-type      (if array?
                           [:vector base-malli-type]
                           base-malli-type)]
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

;; BEYOND MVP
;; - update by query
;; - upsert
;; - insert multi
;; - delete by query
;; - generic query
;; - query only specific columns
;; - resolve fks

(defmethod generate-handler :default
  [_ handler]
  (throw (ex-info "Unsupported handler" {:type    ::unsupported-handler
                                         :handler handler})))