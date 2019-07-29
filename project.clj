(defproject re-frame-utils "0.1.1"
  :description "Fxs and CoFxs for re-frame"
  :url "http://github.com/vimsical/re-frame-utils"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :plugins      [[lein-cljsbuild "1.1.6"]
                 [lein-doo "0.1.7"]]
  :profiles {:provided {:dependencies [[re-frame "0.9.4"]]}
             :dev      {:dependencies [[org.clojure/clojurescript "1.9.671"]
                                       [reagent "0.6.2" :exclusions [org.clojure/clojurescript]]
                                       [re-frame "0.9.4" :exclusions [org.clojure/clojurescript]]
                                       [day8.re-frame/test "0.1.5" :exclusions [org.clojure/clojurescript]]]}}
  :cljsbuild
  {:builds
   [{:id           "test"
     :source-paths ["src/" "test/"]     ; test is needed for the runner
     :test-paths   ["test/"]
     :compiler     {:main           vimsical.re-frame.runner
                    :output-to      "target/cljs-test/test.js"
                    :output-dir     "target/cljs-test"
                    :optimizations  :none
                    :target         :nodejs}}]})
