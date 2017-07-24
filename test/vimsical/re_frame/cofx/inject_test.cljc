(ns vimsical.re-frame.cofx.inject-test
  "Simple test to verify subscription injection.

  - The app-db contains a seq of numbers.

  - We create a sub that takes an optional limit argument returning all or n
  numbers.

  - We create a an event-fx where we inject the numbers sub, sum the elements
  and write the sum out to the db.

  - We assert that the sum is correct.

  What we want to highlight is that when passing a fn to the cofx we're able to
  parametrize the injected subscription as well. In this case we pass a limit to
  the sum event handler, limit gets picked up by the cofx and the value of the
  numbers sub contains a limited range of numbers."
  (:require
   [re-frame.core :as re-frame]
   [day8.re-frame.test :as re-frame.test]
   [vimsical.re-frame.cofx.inject :as sut]
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])))


(def test-db {:numbers (range 10)})


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
     (re-frame/reg-sub
      :numbers
      (fn [db [_ limit]]
        (cond->> (:numbers db)
          (some? limit) (take limit))))
     (re-frame/reg-sub :sum (fn [db _] (:sum db)))
     ;;
     ;; Run test
     ;;
     (f))))


(t/use-fixtures :each re-frame-fixture)


;;
;; * Tests
;;


(t/deftest inject-vec-test
  (re-frame/reg-event-fx
   :update-sum
   [(re-frame/inject-cofx ::sut/sub ^:ignore-dispose [:numbers])]
   (fn [{:keys [db numbers]} _]
     {:db (assoc db :sum (reduce + numbers))}))
  (re-frame/dispatch [:update-sum])
  (t/is (= (reduce + (range 10)) (deref (re-frame/subscribe [:sum])))))


(t/deftest inject-fn-test
  (re-frame/reg-event-fx
   :update-sum
   [(re-frame/inject-cofx ::sut/sub (fn [[_ limit]] ^:ignore-dispose [:numbers limit]))]
   (fn [{:keys [db numbers]} _]
     {:db (assoc db :sum (reduce + numbers))}))
  (re-frame/dispatch [:update-sum 3])
  (t/is (= (reduce + (range 3)) (deref (re-frame/subscribe [:sum])))))
