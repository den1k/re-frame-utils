(ns vimsical.re-frame.runner
  (:require
   [cljs.test :as test]
   [doo.runner :refer-macros [doo-all-tests doo-tests]]
   [vimsical.re-frame.cofx.inject-test]
   [vimsical.re-frame.fx.track-test]))


(doo-all-tests #"vimsical.+\-test$")
