(ns route-craft.query-strings)

;; Goal: Support JSON
;; Query string encodable
;; Secure

#_{:where {:fk_table_id {:eq fk_table_id}}
 :joins {:user_id "left"}
 :limit limit
 :offset offset
 :columns ["id" ...]
 :order [{:id "asc"}]}

;; OPS
;; eq
;; ilike
;; gt
;; gte
;; lt
;; lte
;; in

;; TODO: logical ops, or, and?

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