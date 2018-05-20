(ns date-rule-instaparse.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [date-rule-instaparse.handler :refer :all]))

(deftest test-app
  (testing "main route"
           (let [response (app (mock/request :get "/"))]
             (is (= (:status response) 200))
             (is (= (:body response) "Hello World!"))))

  (testing "date route"
           (let [response (app (mock/request :get "/date/a?text=2018-05-20"))]
             (is (= (:status response) 200))
             (is (= (:body response) "05/20"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
