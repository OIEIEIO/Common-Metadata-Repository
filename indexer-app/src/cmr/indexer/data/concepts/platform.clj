(ns cmr.indexer.data.concepts.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common-app.services.kms-fetcher :as kf]))

(def FIELD_NOT_PRESENT
  "A string to indicate that a field is not present within a KMS keyword."
  "Not Provided")

(def default-platform-values
  (zipmap [:category :series-entity :long-name]
        (repeat FIELD_NOT_PRESENT)))

(defn platform-short-name->elastic-doc
  "Converts a platform into the portion going in an elastic document. If a field is not present in
  the KMS hierarchy we use a dummy value to indicate the field was not present."
  [gcmd-keywords-map short-name]
  (let [full-platform
        (merge default-platform-values
               (kf/get-full-hierarchy-for-short-name gcmd-keywords-map :platforms short-name))
        {:keys [category series-entity long-name uuid]} full-platform]
    {:category category
     :category.lowercase (str/lower-case category)
     :series-entity series-entity
     :series-entity.lowercase (str/lower-case series-entity)
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name.lowercase (str/lower-case long-name)
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))
