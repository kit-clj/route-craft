(ns route-craft.querying
  (:require
    [clojure.string :as string]))

#_{:where   {:fk_table_id {:eq fk_table_id}}
   :joins   {:user_id "left"}
   :limit   limit
   :offset  offset
   :columns ["id" ...]
   :order   [{:id "asc"}]}

(defn qualified-column-kw
  [base-table column]
  (keyword
    (if (string/includes? column "." )
      column
      (str (name base-table) "." (name column)))))

(defn rc-columns->select
  [{:keys [base-table]} columns]
  (mapv (partial qualified-column-kw base-table) columns))

(def honeysql-joins
  {"left"  :left-join
   "right" :right-join
   "inner" :inner-join
   "full"  :full-join
   "cross" :cross-join})
(defn assoc-joins
  [query-map {:keys [base-table columns]} joins]
  (reduce-kv
    (fn [out column-kw join-type]
      (if-let [[target-table target-column] (get-in columns [column-kw :refers-to])]
        (assoc out (get honeysql-joins join-type) [target-table [:=
                                                                 (keyword (str (name target-table) "." (name target-column)))
                                                                 (keyword (str (name base-table) "." (name column-kw)))]])
        (throw (ex-info "No foreign key to join on" {:column column-kw
                                                     :type   ::unsupported-column-join}))))
    query-map
    joins))

(def honeysql-ops
  {:eq     :=
   :neq    :not=
   :like   :like
   :nlike  :not-like
   :ilike  :ilike
   :nilike :not-ilike
   :gt     :>
   :gte    :>=
   :lt     :<
   :lte    :<=
   :in     :in
   :nin    :not-in})

(defn rc-where->hsql-where
  [{:keys [base-table]} where-map]
  (reduce-kv
    (fn [out column-key v]
      (reduce-kv
        (fn [acc op value]
          (conj acc
                [(get honeysql-ops op)
                 (qualified-column-kw base-table column-key)
                 value]))
        out v))
    [:and]
    where-map))

(defn rc-order->hsql-order-by
  [{:keys [base-table]} orders]
  (mapcat
    (fn [order-map]
      (reduce-kv
       (fn [out column-kw order-direction]
         (conj out [(qualified-column-kw base-table column-kw) (keyword order-direction)]))
       []
       order-map))
    orders))

(defn rc-query->query-map
  "Given a validated route-craft query, convert to honeysql query map"
  [{:keys [where joins limit offset columns order]} opts]
  (cond-> {}
          columns (assoc :select (rc-columns->select opts columns))
          joins (assoc-joins opts joins)
          where (assoc :where (rc-where->hsql-where opts where))
          limit (assoc :limit limit)
          offset (assoc :offset offset)
          order (assoc :order-by (rc-order->hsql-order-by opts order))))

;; TODO: maybe this ns is candidate for memoization if enabled via config

(comment

  (q/rc-where->hsql-where
    {:base-table :companies
     :columns (get-in x [:companies :columns])}
    {:name {:eq "asdf"}
     :address.care_of {:eq "qwer"}})
  (q/rc-columns->select
    {:base-table :companies
     :columns (get-in x [:companies :columns])}
    [:name :address.care_of])
  (q/assoc-joins
    {}
    {:base-table :companies
     :columns (get-in x [:tables :companies :columns])}
    {:address_id "left"})
  (q/rc-order->hsql-order-by
    {:base-table :companies
     :columns (get-in x [:tables :companies :columns])}
    [{:id "asc"}]))