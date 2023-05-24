(ns route-craft.xray-ext
  "Extended data given db-xray output")

(defn column-names
  [tables table-prefix columns expand-references?]
  (reduce-kv
    (fn [acc k {:keys [refers-to]}]
      (let [column-key (if table-prefix
                         (keyword (str (name table-prefix) "." (name k)))
                         k)]
        (if (and expand-references? refers-to)
          (let [refers-to-table (first refers-to)]
            (apply conj acc column-key (column-names tables refers-to-table (get-in tables [refers-to-table :columns]) false)))
          (conj acc column-key))))
    #{}
    columns))

(defn extend-db-xray
  [db-xray]
  (update db-xray :tables
          (fn [tables]
            (reduce-kv
              (fn [acc table-kw {:keys [columns] :as table}]
                (assoc acc table-kw
                           (assoc table :rc/permitted-columns (column-names tables nil columns true))))
              {}
              tables))))
