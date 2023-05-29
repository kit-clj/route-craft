(ns route-craft.routes
  (:require
    [clojure.tools.logging :as log]
    [route-craft.handlers :as handlers]))

;; Custom query validation

(def valid-order [:enum
                  "asc"
                  "desc"
                  "asc-nulls-first"
                  "asc-nulls-last"
                  "desc-nulls-first"
                  "desc-nulls-last"])
(def valid-joins [:enum "left" "right" "inner" "full" "cross"])
(def valid-ops [:enum :eq :neq :like :nlike :ilike :nilike :gt :gte :lt :lte :in :nin])

(defn malli-query-params
  [{:rc/keys [permitted-columns]}]
  (let [columns-kw-enum  (vec (cons :enum permitted-columns))
        columns-str-enum (vec (cons :enum (map name permitted-columns)))]
    [:map
     [:where {:optional true} [:map-of columns-kw-enum [:map-of valid-ops any?]]]
     [:joins {:optional true} [:map-of columns-kw-enum valid-joins]]
     [:limit {:optional true} :int]
     [:offset {:optional true} :int]
     [:columns {:optional true} [:vector columns-str-enum]]
     [:order {:optional true} [:vector [:map-of columns-kw-enum valid-order]]]]))

;; ===================ROUTING===================

;; generate crud routes using default handlers
;; permit overriding handlers where specifed in config
;; malli schema for swaggerui

(defn get-pk-from-table
  [{:keys [columns]}]
  (let [pk-cols (reduce-kv (fn [acc k {:keys [primary-key?]}]
                             (if primary-key?
                               (conj acc k)
                               acc))
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
                     generate-handler-fn (partial handlers/generate-handler {:table-name          table
                                                                             :table               dbtable
                                                                             :pk-key              pk-key
                                                                             :malli-type-mappings malli-type-mappings})]
                 (->> (reduce
                        (fn [acc handler]
                          (let [path (case handler
                                       :insert-one ["" :post]
                                       :get-by-pk [pk-key-path :get]
                                       :update-by-pk [pk-key-path :put]
                                       :delete-by-pk [pk-key-path :delete]
                                       (throw (ex-info "Unsupported handler" {:type    ::unsupported-handler
                                                                              :handler handler})))]
                            (assoc-in acc path (generate-handler-fn handler))))
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