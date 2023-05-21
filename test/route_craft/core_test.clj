(ns route-craft.core-test
  (:require
    [clojure.test :refer :all]
    [migratus.core :as migratus]
    [next.jdbc :as jdbc]
    [reitit.core :as reitit]
    [reitit.ring :as ring]
    [route-craft.core :as route-craft]))

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

(use-fixtures :once test-fixture)

(def base-test-opts
  {:default-handlers  [:get-by-pk :insert-one :update-by-pk :delete-by-pk]
   :throw-on-failure? false
   :table-definitions {:company_attachments {:handlers [:insert-one]}}})

(deftest reitit-ring-integration-test
  (testing "reitit ring handler generation integration test"
    (let [router (ring/router
                   (route-craft/generate-reitit-crud-routes
                     (assoc base-test-opts :db-conn (jdbc/get-connection (:datasource (ctx))))))]
      (is (= true (reitit/router? router)))
      (is (= "/attachments/:uuid" (:template (reitit/match-by-path router "/attachments/1"))))
      (is (fn? (ring/ring-handler router))))))