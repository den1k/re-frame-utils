(defproject re-frame-utils "0.1.0-SNAPSHOT"
  :description "Fxs and CoFxs for re-frame"
  :url "http://github.com/vimsical/re-frame-utils"
  :license {:name "MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:provided {:dependencies [[re-frame "0.9.4"]]}
             :dev      {:dependencies [[re-frame "0.9.4"]]}
             :test     {:dependencies [[day8.re-frame/test "0.1.5"]]
                        :plugins      [[lein-cljsbuild "1.1.6"]
                                       [lein-doo "0.1.7"]]}}
  :cljsbuild
  {:builds
   [{:id       "test"
     :compiler {:main           vimsical.re-frame.runner
                :source-paths   ["src/"]
                :test-paths     ["test/"]
                :asset-path     "/js"
                :output-to      "resources/public/js/compiled/vimsical_re_frame.js"
                :output-dir     "resources/public/js/compiled/out"
                :optimizations  :none
                :parallel-build true}}]})
