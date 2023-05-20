(ns route-craft.core-test
  (:require
    [next.jdbc :as jdbc]
    [clojure.test :refer :all]))

(defn ctx
  []
  {:ds (jdbc/get-datasource {:jdbc-url "jdbc:postgresql://127.0.0.1:5432/rc?user=rc&password=rc"})})

(defn test-fixture
  [f]
  (let [ctx {:system/context {:db-conn "foo"}}]
    (f ctx)))