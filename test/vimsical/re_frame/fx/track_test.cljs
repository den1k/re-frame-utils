(ns vimsical.re-frame.fx.track-test
  (:require
   [reagent.core :as r]
   [vimsical.re-frame.fx.track :as sut]
   [day8.re-frame.test :as re-frame.test]
   [cljs.test :as t :include-macros true]
   [re-frame.core :as re-frame]))


(def test-db {:source 0 :sink nil})


;;
;; * Fixtures
;;


(defn re-frame-fixture
  [f]
  (re-frame.test/with-temp-re-frame-state
    (re-frame.test/run-test-sync
     ;;
     ;; Init database
     ;;
     (re-frame/reg-event-db :test-db-init (constantly test-db))
     (re-frame/dispatch [:test-db-init])
     ;;
     ;; Register subs to read the source and sink destinations
     ;;
     (re-frame/reg-sub ::source (fn [db _] (:source db)))
     (re-frame/reg-sub ::sink   (fn [db _] (:sink db)))
     ;;
     ;; Register corresponding setters
     ;;
     (re-frame/reg-event-fx ::update-source (fn [db [_ val]] {:db (assoc db :source val)}))
     (re-frame/reg-event-fx ::update-sink   (fn [db [_ val]] {:db (assoc db :sink val)}))
     ;;
     ;; Reg the fx
     ;;
     (sut/reg-fx)
     ;;
     ;; Run test
     ;;
     (f))))

(t/use-fixtures :each re-frame-fixture)

;;
;; * Tests
;;

(t/deftest track-test
  (re-frame/reg-event-fx
   ::start
   (fn [_ _]
     {:track {:action       :register
              :id           :test
              :subscription [::source]
              :event-fn     (fn [val] (when val [::update-sink (* 10 val)]))}}))
  (re-frame/reg-event-fx
   ::stop
   (fn [_ _]
     {:track {:action :dispose :id :test}}))

  (let [s (re-frame/subscribe [::sink])]
    (t/is (= nil @s))
    (t/testing "dispatch first"
      (re-frame/dispatch [::start])
      (t/is (= 0 @s)))
    (t/testing "track causes sink to update"
      (re-frame/dispatch [::update-source 1])
      (t/is (= 10 @s))
      (re-frame/dispatch [::update-source 2])
      (t/is (= 20 @s)))
    (t/testing "after stopping the track we stop reacting"
      (re-frame/dispatch [::stop])
      (re-frame/dispatch [::update-source 3])
      ;; NOTE without a component, calling dispose on the the sub will dispose
      ;; of it
      (t/is (= nil @s)))))
