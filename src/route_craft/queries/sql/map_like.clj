(ns route-craft.queries.sql.map-like
  (:require
    [route-craft.queries.sql :as sql]))



(def sql->map-like
  (reify
    sql/SQLFromRequestParams
    (sql/eq [this k v]
      )))


(comment
  (def test-query {:a {:eq 13}
                   :b {:gt 5
                       :ne 10}
                   :or [{:a {:eq 5}
                         :b {:eq 6}}]}))