(#_  "
=> lein ring server-headless
")
(ns date-rule-instaparse.handler
  (:refer-clojure :exclude [format])
  (:require [compojure.core :refer :all] ; require = using namesapes
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [date-rule-instaparse.experimental :refer :all])
  (:use [compojure.coercions]
        [java-time]))


(defroutes app-routes
  (context "/date" [] (defroutes users-routes
    (GET "/:id" [id text]
         (let [date (as-localdate text)]
           (format "MM/dd" date)))))
  (context "/number" [] (defroutes users-routes
    (GET "/:name" [name value :<< as-int]
      (str "<h1>" (format "variable %s has value %d" name value) "</h1>"))))
  (context "/documents" [] (defroutes documents-routes
    (GET  "/" [] "get-all-documents")
    (POST "/" {body :body} (format "create-new-document body %s" body ))
    (context "/:id" [id] (defroutes document-routes
      (GET    "/" [] (format "get-document %s" id))
      (PUT    "/" {body :body} (format "update-document %s %s" id body))
      (DELETE "/" [] (format "delete-document %s" id ))))))
  (GET "/" [] "Hello World!")
  (route/not-found "Not Found"))

;;(as-localdate "2018-05-20")
;;[x :<< as-int]

(def app
  (wrap-defaults app-routes site-defaults))
