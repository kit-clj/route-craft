(ns route-craft.queries.sql)

(defprotocol SQLFromRequestParams
  ;; Equality
  (eq [this key value])
  (neq [this key value])
  (is [this key value])
  (distinct [this key value])
  (gt [this key value])
  (gte [this key value])
  (lt [this key value])
  (lte [this key value])
  ;; Partial matching
  (like [this key value])
  (ilike [this key value])
  ;; Collections
  (in [this key value])
  ;; Array operators
  (contains [this key value])
  (contained [this key value])
  (overlap [this key value])
  ;; Logical operator
  (or [this key value])
  (and [this key value])
  (not [this key value])
  (all [this key value])
  (any [this key value]))