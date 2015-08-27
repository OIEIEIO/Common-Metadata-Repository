(ns cmr.system-int-test.search.collection-facets-search-test
  "This tests the retrieving facets when searching for collections"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.mock-echo.client.echo-util :as e]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}
                                          {:grant-all-search? false}))

(defn- make-coll
  "Helper for creating and ingesting a collection"
  [n prov & attribs]
  (d/ingest prov (dc/collection (apply merge {:entry-title (str "coll" n)} attribs))))

;; Attrib functions - These are helpers for creating maps with collection attributes
(defn- projects
  [& project-names]
  {:projects (apply dc/projects project-names)})

(def platform-short-names
  "List of platform short names that exist in the test KMS hierarchy."
  ["AE-A" "AD-A" "DMSP 5D-3/F18"])

(def FROM_KMS
  "Constant indicating that the short name for the field should be a short name found in KMS."
  "FROM_KMS")

(defn- platforms
  "Creates a specified number of platforms each with a certain number of instruments and sensors"
  ([prefix num-platforms]
   (platforms prefix num-platforms 0 0))
  ([prefix num-platforms num-instruments]
   (platforms prefix num-platforms num-instruments 0))
  ([prefix num-platforms num-instruments num-sensors]
   {:platforms
    (for [pn (range 0 num-platforms)
          :let [platform-name (str prefix "-p" pn)]]
      (dc/platform
        {:short-name (if (= FROM_KMS prefix)
                       (or (get platform-short-names pn) platform-name)
                       platform-name)
         :long-name platform-name
         :instruments
         (for [in (range 0 num-instruments)
               :let [instrument-name (str platform-name "-i" in)]]
           (dc/instrument
             {:short-name instrument-name
              :sensors (for [sn (range 0 num-sensors)
                             :let [sensor-name (str instrument-name "-s" sn)]]
                         (dc/sensor {:short-name sensor-name}))}))}))}))

(defn- twod-coords
  [& names]
  {:two-d-coordinate-systems (map dc/two-d names)})


(def sk1 (dc/science-keyword {:category "Cat1"
                              :topic "Topic1"
                              :term "Term1"
                              :variable-level-1 "Level1-1"
                              :variable-level-2 "Level1-2"
                              :variable-level-3 "Level1-3"
                              :detailed-variable "Detail1"}))


(def sk2 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "Extreme"
                              :variable-level-1 "Level2-1"
                              :variable-level-2 "Level2-2"
                              :variable-level-3 "Level2-3"
                              :detailed-variable "UNIVERSAL"}))

(def sk3 (dc/science-keyword {:category "Hurricane"
                              :topic "Popular"
                              :term "UNIVERSAL"}))

(def sk4 (dc/science-keyword {:category "Hurricane"
                              :topic "Cool"
                              :term "Term4"
                              :variable-level-1 "UNIVERSAL"}))

(def sk5 (dc/science-keyword {:category "Tornado"
                              :topic "Popular"
                              :term "Extreme"}))

(def sk6 (dc/science-keyword {:category "UPCASE"
                              :topic "Popular"
                              :term "Mild"}))

(def sk7 (dc/science-keyword {:category "upcase"
                              :topic "Cool"
                              :term "Mild"}))

(def sk8 (dc/science-keyword {:category "Category"
                              :topic "Topic"
                              :term "Term"
                              :variable-level-1 "V-L1"
                              :detailed-variable "Detailed-No-Level2-or-3"}))

(defn- science-keywords
  [& sks]
  {:science-keywords sks})

(defn- processing-level-id
  [id]
  {:processing-level-id id})

(defn- generate-science-keywords
  "Generate science keywords based on a unique number."
  [n]
  (dc/science-keyword {:category (str "Cat-" n)
                       :topic (str "Topic-" n)
                       :term (str "Term-" n)
                       :variable-level-1 "Level1-1"
                       :variable-level-2 "Level1-2"
                       :variable-level-3 "Level1-3"
                       :detailed-variable (str "Detail-" n)}))

(defn- grant-permissions
  "Grant permissions to all collections in PROV1 and a subset of collections in PROV2"
  []
  (e/grant-guest (s/context) (e/coll-catalog-item-id "provguid1"))
  (e/grant-guest (s/context)
                 (e/coll-catalog-item-id "provguid2" (e/coll-id ["coll2" "coll3" "coll5"]))))

(defn- get-facet-results
  "Returns the facets returned by a search in both JSON and XML reference formats. Takes the type
  of facets to be returned - either :flat or :hierarchical. The format of the response is:
  {:xml-facets xml-facets
  :json-facets json-results}"
  [type]
  (index/wait-until-indexed)
  (let [search-options {:page-size 0
                        :include-facets true
                        :hierarchical-facets (= :hierarchical type)}]
    {:xml-facets (:facets (search/find-refs :collection search-options))
     :json-facets (get-in (search/find-concepts-json :collection search-options)
                          [:results :facets])}))

(deftest all-science-keywords-fields-hierarchy
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1"
                         (science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                         (projects "proj1" "PROJ2")
                         (platforms FROM_KMS 2 2 1)
                         (twod-coords "Alpha")
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll2 (make-coll 2 "PROV1"
                         (science-keywords sk1 sk2 sk3 sk4 sk5 sk6 sk7)
                         (projects "proj1" "PROJ2")
                         (platforms FROM_KMS 2 2 1)
                         (twod-coords "Alpha")
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        expected-facets [{:field "archive_center", :value-counts [["Larc" 2]]}
                         {:field "project", :value-counts [["PROJ2" 2] ["proj1" 2]]}
                         {:field "instrument",
                          :value-counts
                          [["FROM_KMS-p0-i0" 2] ["FROM_KMS-p0-i1" 2] ["FROM_KMS-p1-i0" 2]
                           ["FROM_KMS-p1-i1" 2]]}
                         {:field "sensor",
                          :value-counts
                          [["FROM_KMS-p0-i0-s0" 2] ["FROM_KMS-p0-i1-s0" 2] ["FROM_KMS-p1-i0-s0" 2]
                           ["FROM_KMS-p1-i1-s0" 2]]}
                         {:field "two_d_coordinate_system_name",
                          :value-counts [["Alpha" 2]]}
                         {:field "processing_level_id", :value-counts [["PL1" 2]]}
                         {:field "detailed_variable",
                          :value-counts [["Detail1" 2] ["UNIVERSAL" 2]]}
                         {:field "platforms",
                          :subfields ["category"],
                          :category
                          [{:value "Earth Observation Satellites",
                            :count 2,
                            :subfields ["series-entity"],
                            :series-entity
                            [{:value "AD (Atmospheric Dynamics)",
                              :count 2,
                              :subfields ["short-name"],
                              :short-name
                              [{:value "AD-A",
                                :count 2,
                                :subfields ["long-name"],
                                :long-name
                                [{:value "Atmosphere Dynamics A (Explorer 19)",
                                  :count 2}]}]}
                             {:value "AE (Atmosphere Explorer)",
                              :count 2,
                              :subfields ["short-name"],
                              :short-name
                              [{:value "AE-A",
                                :count 2,
                                :subfields ["long-name"],
                                :long-name
                                [{:value "Atmosphere Explorer A (Explorer 17)",
                                  :count 2}]}]}]}]}
                         {:field "science_keywords",
                          :subfields ["category"],
                          :category
                          [{:value "Hurricane",
                            :count 2,
                            :subfields ["topic"],
                            :topic
                            [{:value "Popular",
                              :count 2,
                              :subfields ["term"],
                              :term
                              [{:value "Extreme",
                                :count 2,
                                :subfields ["variable-level-1"],
                                :variable-level-1
                                [{:value "Level2-1",
                                  :count 2,
                                  :subfields ["variable-level-2"],
                                  :variable-level-2
                                  [{:value "Level2-2",
                                    :count 2,
                                    :subfields ["variable-level-3"],
                                    :variable-level-3
                                    [{:value "Level2-3", :count 2}]}]}]}
                               {:value "UNIVERSAL", :count 2}]}
                             {:value "Cool",
                              :count 2,
                              :subfields ["term"],
                              :term
                              [{:value "Term4",
                                :count 2,
                                :subfields ["variable-level-1"],
                                :variable-level-1
                                [{:value "UNIVERSAL", :count 2}]}]}]}
                           {:value "Cat1",
                            :count 2,
                            :subfields ["topic"],
                            :topic
                            [{:value "Topic1",
                              :count 2,
                              :subfields ["term"],
                              :term
                              [{:value "Term1",
                                :count 2,
                                :subfields ["variable-level-1"],
                                :variable-level-1
                                [{:value "Level1-1",
                                  :count 2,
                                  :subfields ["variable-level-2"],
                                  :variable-level-2
                                  [{:value "Level1-2",
                                    :count 2,
                                    :subfields ["variable-level-3"],
                                    :variable-level-3
                                    [{:value "Level1-3", :count 2}]}]}]}]}]}
                           {:value "Tornado",
                            :count 2,
                            :subfields ["topic"],
                            :topic
                            [{:value "Popular",
                              :count 2,
                              :subfields ["term"],
                              :term [{:value "Extreme", :count 2}]}]}
                           {:value "UPCASE",
                            :count 2,
                            :subfields ["topic"],
                            :topic
                            [{:value "Popular",
                              :count 2,
                              :subfields ["term"],
                              :term [{:value "Mild", :count 2}]}]}
                           {:value "upcase",
                            :count 2,
                            :subfields ["topic"],
                            :topic
                            [{:value "Cool",
                              :count 2,
                              :subfields ["term"],
                              :term [{:value "Mild", :count 2}]}]}]}]
        actual-facets (get-facet-results :hierarchical)]
    (is (= expected-facets (:xml-facets actual-facets)))
    (is (= expected-facets (:json-facets actual-facets)))))

;; The purpose of the test is to make sure when the same topic "Popular" is used under two different
;; categories, the flat facets correctly say 2 collections have the "Popular topic and the
;; hierarchical facets report just one collection with "Popular" below each category.
(deftest nested-duplicate-topics
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1" (science-keywords sk3))
        coll2 (make-coll 2 "PROV1" (science-keywords sk5))
        expected-hierarchical-facets [{:field "archive_center", :value-counts []}
                                      {:field "project", :value-counts []}
                                      {:field "instrument", :value-counts []}
                                      {:field "sensor", :value-counts []}
                                      {:field "two_d_coordinate_system_name", :value-counts []}
                                      {:field "processing_level_id", :value-counts []}
                                      {:field "detailed_variable", :value-counts []}
                                      {:field "platforms", :subfields []}
                                      {:field "science_keywords",
                                       :subfields ["category"],
                                       :category
                                       [{:value "Hurricane",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "Popular",
                                           :count 1,
                                           :subfields ["term"],
                                           :term [{:value "UNIVERSAL", :count 1}]}]}
                                        {:value "Tornado",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "Popular",
                                           :count 1,
                                           :subfields ["term"],
                                           :term [{:value "Extreme", :count 1}]}]}]}]
        expected-flat-facets [{:field "archive_center", :value-counts []}
                              {:field "project", :value-counts []}
                              {:field "platform", :value-counts []}
                              {:field "instrument", :value-counts []}
                              {:field "sensor", :value-counts []}
                              {:field "two_d_coordinate_system_name", :value-counts []}
                              {:field "processing_level_id", :value-counts []}
                              {:field "category",
                               :value-counts [["Hurricane" 1] ["Tornado" 1]]}
                              {:field "topic", :value-counts [["Popular" 2]]}
                              {:field "term",
                               :value-counts [["Extreme" 1] ["UNIVERSAL" 1]]}
                              {:field "variable_level_1", :value-counts []}
                              {:field "variable_level_2", :value-counts []}
                              {:field "variable_level_3", :value-counts []}
                              {:field "detailed_variable", :value-counts []}]
        actual-hierarchical-facets (get-facet-results :hierarchical)
        actual-flat-facets (get-facet-results :flat)]
    (is (= expected-hierarchical-facets (:xml-facets actual-hierarchical-facets)))
    (is (= expected-hierarchical-facets (:json-facets actual-hierarchical-facets)))
    (is (= expected-flat-facets (:xml-facets actual-flat-facets)))
    (is (= expected-flat-facets (:json-facets actual-flat-facets)))))

(deftest empty-hierarchical-facets-test
  (let [expected-facets [{:field "archive_center", :value-counts []}
                         {:field "project", :value-counts []}
                         {:field "instrument", :value-counts []}
                         {:field "sensor", :value-counts []}
                         {:field "two_d_coordinate_system_name", :value-counts []}
                         {:field "processing_level_id", :value-counts []}
                         {:field "detailed_variable", :value-counts []}
                         {:field "platforms", :subfields []}
                         {:field "science_keywords", :subfields []}]
        actual-facets (get-facet-results :hierarchical)]
    (is (= expected-facets (:xml-facets actual-facets)))
    (is (= expected-facets (:json-facets actual-facets)))))

(deftest detailed-variable-test
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1" (science-keywords sk8))
        expected-hierarchical-facets [{:field "archive_center", :value-counts []}
                                      {:field "project", :value-counts []}
                                      {:field "instrument", :value-counts []}
                                      {:field "sensor", :value-counts []}
                                      {:field "two_d_coordinate_system_name", :value-counts []}
                                      {:field "processing_level_id", :value-counts []}
                                      {:field "detailed_variable",
                                       :value-counts [["Detailed-No-Level2-or-3" 1]]}
                                      {:field "platforms", :subfields []}
                                      {:field "science_keywords",
                                       :subfields ["category"],
                                       :category
                                       [{:value "Category",
                                         :count 1,
                                         :subfields ["topic"],
                                         :topic
                                         [{:value "Topic",
                                           :count 1,
                                           :subfields ["term"],
                                           :term
                                           [{:value "Term",
                                             :count 1,
                                             :subfields ["variable-level-1"],
                                             :variable-level-1 [{:value "V-L1", :count 1}]}]}]}]}]
        expected-flat-facets [{:field "archive_center", :value-counts []}
                              {:field "project", :value-counts []}
                              {:field "platform", :value-counts []}
                              {:field "instrument", :value-counts []}
                              {:field "sensor", :value-counts []}
                              {:field "two_d_coordinate_system_name", :value-counts []}
                              {:field "processing_level_id", :value-counts []}
                              {:field "category", :value-counts [["Category" 1]]}
                              {:field "topic", :value-counts [["Topic" 1]]}
                              {:field "term", :value-counts [["Term" 1]]}
                              {:field "variable_level_1", :value-counts [["V-L1" 1]]}
                              {:field "variable_level_2", :value-counts []}
                              {:field "variable_level_3", :value-counts []}
                              {:field "detailed_variable",
                               :value-counts [["Detailed-No-Level2-or-3" 1]]}]
        actual-hierarchical-facets (get-facet-results :hierarchical)
        actual-flat-facets (get-facet-results :flat)]
    (is (= expected-hierarchical-facets (:xml-facets actual-hierarchical-facets)))
    (is (= expected-hierarchical-facets (:json-facets actual-hierarchical-facets)))
    (is (= expected-flat-facets (:xml-facets actual-flat-facets)))
    (is (= expected-flat-facets (:json-facets actual-flat-facets)))))

(deftest flat-facets-test
  (grant-permissions)
  (let [coll1 (make-coll 1 "PROV1"
                         (projects "proj1" "PROJ2")
                         (platforms "A" 2 2 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1 sk4 sk5)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll2 (make-coll 2 "PROV2"
                         (projects "proj3" "PROJ2")
                         (platforms "B" 2 2 1)
                         (science-keywords sk1 sk2 sk3)
                         (processing-level-id "pl1")
                         {:organizations [(dc/org :archive-center "GSFC")]})
        coll3 (make-coll 3 "PROV2"
                         (platforms "A" 1 1 1)
                         (twod-coords "Alpha" "Bravo")
                         (science-keywords sk5 sk6 sk7)
                         (processing-level-id "PL1")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll4 (make-coll 4 "PROV1"
                         (twod-coords "alpha")
                         (science-keywords sk3)
                         (processing-level-id "PL2")
                         {:organizations [(dc/org :archive-center "Larc")]})
        coll5 (make-coll 5 "PROV2")

        ;; Guests do not have permission to this collection so it will not appear in results
        coll6 (make-coll 6 "PROV2"
                         (projects "proj1")
                         (platforms "A" 1 1 1)
                         (twod-coords "Alpha")
                         (science-keywords sk1)
                         (processing-level-id "PL1"))
        all-colls [coll1 coll2 coll3 coll4 coll5 coll6]]

    (index/wait-until-indexed)

    (testing "invalid include-facets"
      (is (= {:errors ["Parameter include_facets must take value of true, false, or unset, but was [foo]"] :status 400}
             (search/find-refs :collection {:include-facets "foo"})))
      (is (= {:errors ["Parameter [include_facets] was not recognized."] :status 400}
             (search/find-refs :granule {:include-facets true}))))

    (testing "retreving all facets in different formats"
      (let [expected-facets [{:field "archive_center"
                              :value-counts [["Larc" 3] ["GSFC" 1]]}
                             {:field "project"
                              :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                             {:field "platform"
                              :value-counts [["A-p0" 2] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                             {:field "instrument"
                              :value-counts [["A-p0-i0" 2]
                                             ["A-p0-i1" 1]
                                             ["A-p1-i0" 1]
                                             ["A-p1-i1" 1]
                                             ["B-p0-i0" 1]
                                             ["B-p0-i1" 1]
                                             ["B-p1-i0" 1]
                                             ["B-p1-i1" 1]]}
                             {:field "sensor"
                              :value-counts [["A-p0-i0-s0" 2]
                                             ["A-p0-i1-s0" 1]
                                             ["A-p1-i0-s0" 1]
                                             ["A-p1-i1-s0" 1]
                                             ["B-p0-i0-s0" 1]
                                             ["B-p0-i1-s0" 1]
                                             ["B-p1-i0-s0" 1]
                                             ["B-p1-i1-s0" 1]]}
                             {:field "two_d_coordinate_system_name"
                              :value-counts [["Alpha" 2] ["Bravo" 1] ["alpha" 1]]}
                             {:field "processing_level_id"
                              :value-counts [["PL1" 2] ["PL2" 1] ["pl1" 1]]}
                             {:field "category"
                              :value-counts [["Hurricane" 3]
                                             ["Cat1" 2]
                                             ["Tornado" 2]
                                             ["UPCASE" 1]
                                             ["upcase" 1]]}
                             {:field "topic"
                              :value-counts [["Popular" 4] ["Cool" 2] ["Topic1" 2]]}
                             {:field "term"
                              :value-counts [["Extreme" 3]
                                             ["Term1" 2]
                                             ["UNIVERSAL" 2]
                                             ["Mild" 1]
                                             ["Term4" 1]]}
                             {:field "variable_level_1"
                              :value-counts [["Level1-1" 2] ["Level2-1" 1] ["UNIVERSAL" 1]]}
                             {:field "variable_level_2"
                              :value-counts [["Level1-2" 2] ["Level2-2" 1]]}
                             {:field "variable_level_3"
                              :value-counts [["Level1-3" 2] ["Level2-3" 1]]}
                             {:field "detailed_variable"
                              :value-counts [["Detail1" 2] ["UNIVERSAL" 1]]}]]
        (testing "refs"
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true})))))

        (testing "refs echo-compatible true"
          (is (= expected-facets
                 (search/find-refs :collection {:include-facets true
                                                :echo-compatible true}))))
        (testing "metadata items and direct transformer"
          (is (= expected-facets
                 (:facets (search/find-metadata :collection
                                                :echo10
                                                {:include-facets true
                                                 :concept-id (map :concept-id all-colls)})))))
        (testing "atom"
          (is (= expected-facets
                 (:facets (:results (search/find-concepts-atom :collection {:include-facets true}))))))
        (testing "json"
          (is (= expected-facets
                 (:facets (:results (search/find-concepts-json :collection {:include-facets true}))))))
        (testing "json echo-compatible true"
          (is (= (sort-by :field expected-facets)
                 (sort-by :field (search/find-concepts-json :collection {:include-facets true
                                                                         :echo-compatible true})))))))

    (testing "Search conditions narrow reduce facet values found"
      (testing "search finding two documents"
        (let [expected-facets [{:field "archive_center"
                                :value-counts [["GSFC" 1] ["Larc" 1]]}
                               {:field "project"
                                :value-counts [["PROJ2" 2] ["proj1" 1] ["proj3" 1]]}
                               {:field "platform"
                                :value-counts [["A-p0" 1] ["A-p1" 1] ["B-p0" 1] ["B-p1" 1]]}
                               {:field "instrument"
                                :value-counts [["A-p0-i0" 1]
                                               ["A-p0-i1" 1]
                                               ["A-p1-i0" 1]
                                               ["A-p1-i1" 1]
                                               ["B-p0-i0" 1]
                                               ["B-p0-i1" 1]
                                               ["B-p1-i0" 1]
                                               ["B-p1-i1" 1]]}
                               {:field "sensor"
                                :value-counts [["A-p0-i0-s0" 1]
                                               ["A-p0-i1-s0" 1]
                                               ["A-p1-i0-s0" 1]
                                               ["A-p1-i1-s0" 1]
                                               ["B-p0-i0-s0" 1]
                                               ["B-p0-i1-s0" 1]
                                               ["B-p1-i0-s0" 1]
                                               ["B-p1-i1-s0" 1]]}
                               {:field "two_d_coordinate_system_name" :value-counts [["Alpha" 1]]}
                               {:field "processing_level_id"
                                :value-counts [["PL1" 1] ["pl1" 1]]}
                               {:field "category"
                                :value-counts [["Cat1" 2] ["Hurricane" 2] ["Tornado" 1]]}
                               {:field "topic"
                                :value-counts [["Popular" 2] ["Topic1" 2] ["Cool" 1]]}
                               {:field "term"
                                :value-counts [["Extreme" 2] ["Term1" 2] ["Term4" 1] ["UNIVERSAL" 1]]}
                               {:field "variable_level_1"
                                :value-counts [["Level1-1" 2] ["Level2-1" 1] ["UNIVERSAL" 1]]}
                               {:field "variable_level_2"
                                :value-counts [["Level1-2" 2] ["Level2-2" 1]]}
                               {:field "variable_level_3"
                                :value-counts [["Level1-3" 2] ["Level2-3" 1]]}
                               {:field "detailed_variable"
                                :value-counts [["Detail1" 2] ["UNIVERSAL" 1]]}]]
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :project "PROJ2"}))))))

      (testing "AND conditions narrow facets via AND not OR"
        (let [expected-facets [{:field "archive_center"
                                :value-counts [["GSFC" 1]]}
                               {:field "project" :value-counts [["PROJ2" 1] ["proj3" 1]]}
                               {:field "platform" :value-counts [["B-p0" 1] ["B-p1" 1]]}
                               {:field "instrument"
                                :value-counts [["B-p0-i0" 1]
                                               ["B-p0-i1" 1]
                                               ["B-p1-i0" 1]
                                               ["B-p1-i1" 1]]}
                               {:field "sensor"
                                :value-counts [["B-p0-i0-s0" 1]
                                               ["B-p0-i1-s0" 1]
                                               ["B-p1-i0-s0" 1]
                                               ["B-p1-i1-s0" 1]]}
                               {:field "two_d_coordinate_system_name" :value-counts []}
                               {:field "processing_level_id" :value-counts [["pl1" 1]]}
                               {:field "category" :value-counts [["Cat1" 1] ["Hurricane" 1]]}
                               {:field "topic" :value-counts [["Popular" 1] ["Topic1" 1]]}
                               {:field "term" :value-counts [["Extreme" 1]
                                                             ["Term1" 1]
                                                             ["UNIVERSAL" 1]]}
                               {:field "variable_level_1":value-counts [["Level1-1" 1]
                                                                        ["Level2-1" 1]]}
                               {:field "variable_level_2" :value-counts [["Level1-2" 1]
                                                                         ["Level2-2" 1]]}
                               {:field "variable_level_3" :value-counts [["Level1-3" 1]
                                                                         ["Level2-3" 1]]}
                               {:field "detailed_variable" :value-counts [["Detail1" 1]
                                                                          ["UNIVERSAL" 1]]}]]


          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :project ["PROJ2" "proj3"]
                                                         "options[project][and]" true}))))))

      (testing "search finding one document"
        (let [expected-facets [{:field "archive_center" :value-counts [["Larc" 1]]}
                               {:field "project" :value-counts []}
                               {:field "platform" :value-counts []}
                               {:field "instrument" :value-counts []}
                               {:field "sensor" :value-counts []}
                               {:field "two_d_coordinate_system_name" :value-counts [["alpha" 1]]}
                               {:field "processing_level_id" :value-counts [["PL2" 1]]}
                               {:field "category" :value-counts [["Hurricane" 1]]}
                               {:field "topic" :value-counts [["Popular" 1]]}
                               {:field "term" :value-counts [["UNIVERSAL" 1]]}
                               {:field "variable_level_1" :value-counts []}
                               {:field "variable_level_2" :value-counts []}
                               {:field "variable_level_3" :value-counts []}
                               {:field "detailed_variable" :value-counts []}]]
          (is (= expected-facets
                 (:facets (search/find-refs :collection {:include-facets true
                                                         :processing-level-id "PL2"}))))))

      (let [empty-facets [{:field "archive_center" :value-counts []}
                          {:field "project" :value-counts []}
                          {:field "platform" :value-counts []}
                          {:field "instrument" :value-counts []}
                          {:field "sensor" :value-counts []}
                          {:field "two_d_coordinate_system_name" :value-counts []}
                          {:field "processing_level_id" :value-counts []}
                          {:field "category" :value-counts []}
                          {:field "topic" :value-counts []}
                          {:field "term" :value-counts []}
                          {:field "variable_level_1" :value-counts []}
                          {:field "variable_level_2" :value-counts []}
                          {:field "variable_level_3" :value-counts []}
                          {:field "detailed_variable" :value-counts []}]]
        (testing "search finding one document with no faceted fields"
          (is (= empty-facets
                 (:facets (search/find-refs :collection {:include-facets true :entry-title "coll5"})))))

        (testing "search finding no documents"
          (is (= empty-facets
                 (:facets (search/find-refs :collection {:include-facets true :entry-title "foo"})))))))))

(deftest large-hierarchy-science-keyword-test
  (grant-permissions)
  (let [science-keywords (for [n (range 25)]
                           (generate-science-keywords n))
        _ (make-coll 1 "PROV1" {:science-keywords science-keywords})
        categories (->> (get-facet-results :hierarchical)
                        :json-facets
                        (filter #(= "science_keywords" (:field %)))
                        first
                        :category
                        (map :value))]
    ;; Make sure that all 25 individual categories are returned in the facets
    (is (= (set (map :category science-keywords))
           (set categories)))))

(deftest platform-missing-fields-test
  (grant-permissions)
  ;; Test that if the platforms do not exist in KMS, they will still be returned, but with a value
  ;; of "not provided" for all of the values in the hierarchy other than short name.
  (make-coll 1 "PROV1" (platforms "Platform" 2 2 1))
  ;; Test that even with a nil series-entity the platform will still be returned, but with a
  ;; value of "not provided" for the series-entity
  (make-coll 2 "PROV1" {:platforms [(dc/platform {:short-name "A340-600"})]})
  (let [expected-platforms [{:subfields ["category"],
                             :field "platforms",
                             :category
                             [{:count 1,
                               :value "not provided",
                               :subfields ["series-entity"],
                               :series-entity
                               [{:count 1,
                                 :value "not provided",
                                 :subfields ["short-name"],
                                 :short-name
                                 [{:count 1,
                                   :value "platform-p0",
                                   :subfields ["long-name"],
                                   :long-name [{:count 1, :value "not provided"}]}
                                  {:count 1,
                                   :value "platform-p1",
                                   :subfields ["long-name"],
                                   :long-name [{:count 1, :value "not provided"}]}]}]}
                              {:value "aircraft",
                               :count 1,
                               :subfields ["series-entity"],
                               :series-entity
                               [{:value "not provided",
                                 :count 1,
                                 :subfields ["short-name"],
                                 :short-name
                                 [{:value "a340-600",
                                   :count 1,
                                   :subfields ["long-name"],
                                   :long-name
                                   [{:value "airbus a340-600", :count 1}]}]}]}]}]
        actual-platforms (->> (get-facet-results :hierarchical)
                        :json-facets
                        (filter #(= "platforms" (:field %))))]
    (is (= expected-platforms actual-platforms))))

