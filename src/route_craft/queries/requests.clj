(ns route-craft.queries.requests)

(defmulti query-params->sql (fn [type _query-params] type))

(defmethod query-params->sql :honeysql
  [_ query-params]


  )

(defmethod query-params->sql :default
  [type _]
  (throw (ex-info "Unsupported query type" {:type       ::unsupported-query-type
                                            :query-type type})))