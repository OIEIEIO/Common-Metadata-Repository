(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]))

(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :invalid-data 422
   :conflict 409})

(def internal-error-ring-response
  {:status 500 :body {:errors ["An Internal Error has occurred."]} :content-type :json})

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions."
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        (error e)
        (let [{:keys [type errors]} (ex-data e)
              status-code (type->http-status-code type)]
          (if status-code
            {:status status-code
             :headers {"Content-Type" "application/json"}
             :body {:errors errors}}
            (do
              (error (str "Type [" type "] not recognized."))
              internal-error-ring-response))))
      (catch Exception e
        (error e)
        internal-error-ring-response))))