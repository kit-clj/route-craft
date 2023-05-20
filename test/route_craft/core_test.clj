(ns route-craft.core-test
  (:require
    [clojure.test :refer :all]
    [migratus.core :as migratus]
    [next.jdbc :as jdbc]))

(defn ctx
  []
  {:datasource (jdbc/get-datasource {:jdbcUrl "jdbc:postgresql://127.0.0.1:5432/rc?user=rc&password=rc"})})

(defn test-fixture
  [f]
  (let [migratus-config {:migration-dir "test/resources/migrations"
                         :db            (ctx)
                         :store         :database}]
    (migratus/migrate migratus-config)
    (f)
    (migratus/rollback migratus-config)))

(deftest breaking-test
  (testing "test gha"
    (ctx)
    (is (= 1 2))))