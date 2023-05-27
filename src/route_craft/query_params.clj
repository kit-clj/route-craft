(ns route-craft.query-params)

;; Goal: Support JSON
;; Query string encodable
;; Secure

#_{:where   {:fk_table_id {:eq fk_table_id}}
 :joins   {:user_id "left"}
 :limit   limit
 :offset  offset
 :columns ["id" ...]
 :order   [{:id "asc"}]}

(def valid-order [:enum "asc" "desc"])
(def valid-joins [:enum "left" "right" "inner" "full" "cross"])
(def valid-ops [:enum :eq :neq :ilike :gt :gte :lt :lte :in :nin])

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

;; OPS
;; eq
;; neq
;; ilike
;; gt
;; gte
;; lt
;; lte
;; in
;; nin

;; table key
;; db-xray
;; refers-to permitted in joins
;; columns only permitted OR join columns only permitted
;; column checks on:
;;  - where
;;  - joins
;;  - columns
;;  - order keys
;; validate order vals as asc or desc
;; validate limit and offset as int