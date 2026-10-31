(comment "lein ring server-headless")
(ns hello-world.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defroutes app-routes
  (context "/documents" [] (defroutes documents-routes
    (GET  "/" [] "get-all-documents")
    (POST "/" {body :body} (format "create-new-document body %s" body ))
    (context "/:id" [id] (defroutes document-routes
      (GET    "/" [] (format "get-document %s" id))
      (PUT    "/" {body :body} (format "update-document %s %s" id body))
      (DELETE "/" [] (format "delete-document %s" id ))))))
  (GET "/" [] "Hello World!")
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
