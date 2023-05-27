(ns route-craft.routes
  (:require
    [clojure.tools.logging :as log]
    [route-craft.handlers :as handlers]))

;; ===================ROUTING===================

;; generate crud routes using default handlers
;; permit overriding handlers where specifed in config
;; malli schema for swaggerui

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
                     generate-handler-fn (partial handlers/generate-handler {:table-name          table
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