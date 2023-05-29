(ns route-craft.xray-ext
  "Extended data given db-xray output"
  (:require [clojure.string :as string]))

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

;; TODO: refactor, test
;; TODO: limit rc/permitted-columns
(defn extend-db-xray
  [db-xray]
  (update db-xray :tables
          (fn [tables]
            (reduce-kv
              (fn [acc table-kw {:keys [columns] :as table}]
                (assoc acc table-kw
                           (-> table
                               (assoc :rc/permitted-columns (column-names tables nil columns true))
                               (update :columns (fn [columns]
                                                  (reduce-kv (fn [acc column-key {:keys [column-type] :as column}]
                                                               (assoc acc column-key
                                                                          (let [array? (boolean
                                                                                         (some-> column-type
                                                                                                 (name)
                                                                                                 (string/starts-with? "_")))]
                                                                            (cond-> column
                                                                                    true (assoc :rc/array? array?)
                                                                                    array? (update :column-type #(keyword (string/replace (name %) #"^_" "")))))))
                                                             {}
                                                             columns))))))
              {}
              tables))))
