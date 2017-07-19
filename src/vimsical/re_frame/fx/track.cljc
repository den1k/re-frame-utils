(ns re-frame-utils.track
  "The track fx allows dispatching an event every time the value of a subscription changes."
  (:require
   [re-frame.core :as re-frame]))

;;
;; * Register
;;

(defonce track-register (atom {}))

;;
;; * Internal helpers
;;

(defn new-track
  "Create a new reagent track that will execute every time `subscription`
  updates.

  If `event` is provided will always dispatch that event.

  If event is nil, `val->event` is required and will be invoked with the latest
  value from the subscription. It should return an event to dispatch or nil for
  a no-op.

  "
  [{:keys [subscription event val->event dispatch-first?] :or {dispatch-first? true}}]
  {:pre [(vector? subscription) (or (vector? event) (ifn? val->event))]}
  #?(:cljs
     (let [dispatched-first? (atom false)]
       (ratom/track!
        (fn []
          (let [val @(re-frame/subscribe subscription)]
            (when-some [event (or event (val->event val))]
              (when (or dispatch-first?
                        @dispatched-first?
                        (do (reset! dispatched-first? true) nil))
                (re-frame/dispatch event)))))))))

;;
;; * Fx handler
;;

(defmulti track-fx* :action)

(defmethod track-fx* :register
  [{:keys [id] :as track}]
  (if-some [track' (get @track-register id)]
    (throw (ex-info "Track already exists" {:track track' :tried track}))
    (let [track (new-track track)]
      (swap! track-register assoc id track))))

(defmethod track-fx* :dispose
  [{:keys [id] :as track}]
  (if-some [track (get @track-register id)]
    #?(:cljs (do (ratom/dispose! track) (swap! track-register dissoc id)) :clj nil)
    (throw (ex-info "Track isn't registered" {:track track}))))

(defn track-fx
  [track-or-tracks]
  (letfn [(ensure-vec [x]
            (if (sequential? x) (vec x) (vector x)))]
    (doseq [track (ensure-vec track-or-tracks)]
      (track-fx* track))))

;;
;; * Entry point
;;

(defn reg-fx
  ([] (reg-fx :track))
  ([fx] (re-frame/reg-fx fx track-fx)))

(comment
  (do
    (require '[re-frame.interop :as interop])
    (re-frame/reg-event-fx
     ::start-trigger-track
     (fn [cofx event]
       {:track
        {:action       :register
         :id           42
         :subscription [::query-vector "blah"]
         :val->event   (fn [val] [::trigger val])}}))

    (re-frame/reg-event-fx
     ::stop-trigger-track
     (fn [cofx event]
       {:track
        {:action :dispose
         :id     42}}))
    ;; Define a sub and the event we want to trigger
    (defonce foo (interop/ratom 0))
    (re-frame/reg-sub-raw ::query-vector (fn [_ _] (interop/make-reaction #(deref foo))))
    (re-frame/reg-event-db ::trigger (fn [db val] (println "Trigger" val) db))
    ;; Start the track
    (re-frame/dispatch [::start-trigger-track])
    ;; Update the ::query-vector, will cause ::trigger to run
    (swap! foo inc)
    (swap! foo inc)
    (swap! foo inc)
    ;; Stop the track, updates to ::query-vector aren't tracked anymore
    (re-frame/dispatch [::stop-trigger-track])
    (swap! foo inc)))
