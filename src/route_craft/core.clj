(ns route-craft.core
  (:require
    [clojure.tools.logging :as log]
    [donut.dbxray :as dbx]
    [kit.edge.db.postgres]
    [route-craft.routes :as routes]
    [route-craft.xray-ext :as xray-ext]))

(def ^:private base-malli-type-mappings
  {:integer     :int
   :bool        :boolean
   :text        :string
   :varchar     :string
   :uuid        :uuid
   :bigint      :int
   :timestamptz :time/instant
   :timestamp   :time/instant
   :timetz      :time/local-time
   :date        :time/local-date
   :jsonb       :any
   :hstore      :any
   :oid         :string})

;; ===================CONFIGURATION===================

;; specify which routes have which CRUD capabilities
;; ignore certain tables
;; permission definitions

;; ignore certain columns / hide columns ?
;; handling defaults

(defn generate-reitit-crud-routes
  ""
  [{:keys [table-definitions
           role-parser-fn
           malli-type-mappings
           default-handlers
           throw-on-failure?
           db-conn]
    :as   opts}]
  (try
    (let [db-xray (-> db-conn (dbx/xray) (xray-ext/extend-db-xray))]
      (routes/routes-from-dbxray (update opts :malli-type-mappings #(merge base-malli-type-mappings %)) db-xray))
    (catch Exception e
      (log/error e "Failed to create reitit routes")
      (when throw-on-failure?
        (throw e)))))


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


;; Future work:
;; - caching