(ns solsort.misc
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer  [go go-loop alt!]])
  (:require
    [cljs.core.async.impl.channels :refer  [ManyToManyChannel]]
    [cljs.core.async :refer  [>! <! chan put! take! timeout close! pipe]]
    [cljs.test :refer-macros  [deftest testing is run-tests]]
    [clojure.string :as string :refer  [split]]
    [clojure.string :refer  [join]]
    [cognitect.transit :as transit]
    [re-frame.core :as re-frame :refer [register-sub subscribe]]
    [goog.net.Jsonp]
    [goog.net.XhrIo]
    [re-frame.core :as re-frame :refer [register-sub subscribe register-handler dispatch]]
    [reagent.core :as reagent :refer  []]))

(enable-console-print!)


(register-sub :log (fn [db _] (reaction (:log @db))))
;; # logger
(defn log [& args]
  (apply print 'log args)
  (dispatch (into  [:log] args)))

(register-handler
  :log (fn [db [_ & entry] _]
         (let [q (or (:log db) cljs.core/PersistentQueue.EMPTY)
               q (if (<= 30 (count q)) (pop q) q)
               q (conj q entry)]
           (assoc db :log q))))
;; # misc
;; ## run-once
(defn run-once [f]
  (let [do-run (atom true)]
    (fn [& args]
      (when @do-run
        (reset! do-run false)
        (apply f args)))))

;; ## js-json
(defn parse-json-or-nil [str]
  (try
    (js/JSON.parse str)
    (catch :default _ nil)))

(defn jsextend [target source]
  (let [ks (js/Object.keys source)]
    (while (pos? (.-length ks))
      (let [k (.pop ks)] (aset target k (aget source k)))))
  target)

;; ## tests
(defn chan? [c] (instance? ManyToManyChannel c))
(defn function? [c] (instance? js/Function c))
;; ## async channels

(defn go<!-seq [cs]
  (go
    (loop [acc []
           cs cs]
      (if (first cs)
        (recur (conj acc (<! (first cs)))
               (rest cs))
        acc))))

(defn print-channel [c]
  (go (loop [msg (<! c)]
        (when msg (print msg) (recur (<! c))))))


;; ## transducers
(defn by-first [xf]
  (let [prev-key (atom nil)
        values (atom '())]
    (fn
      ([result]
       (when (pos? (count @values))
         (xf result [@prev-key @values])
         (reset! values '()))
       (xf result))
      ([result input]
       (if (= (first input) @prev-key)
         (swap! values conj (rest input))
         (do
           (if (pos? (count @values)) (xf result [@prev-key @values]))
           (reset! prev-key (first input))
           (reset! values (list (rest input)))))))))

(defn transducer-status [& s]
  (fn [xf]
    (let [prev-time (atom 0)
          cnt (atom 0)]
      (fn
        ([result]
         (apply log (concat s (list 'done)))
         (xf result))
        ([result input]
         (swap! cnt inc)
         (when (< 60000 (- (.now js/Date) @prev-time))
           (reset! prev-time (.now js/Date))
           (apply log (concat s (list @cnt))))
         (xf result input))))))

(defn transducer-accumulate [initial]
  (fn [xf]
    (let [acc (atom initial)]
      (fn
        ([result]
         (when @acc
           (xf result @acc)
           (reset! acc nil))
         (xf result))
        ([result input]
         (swap! acc conj input))))))

(def group-lines-by-first
  (comp
    by-first
    (map (fn [[k v]] [k (map (fn [[s]] s) v)]))))

; string
(defn parse-path [path] (.split (.slice path 1) #"[/.]"))

(defn canonize-string [s]
  (.replace (.trim (.toLowerCase s))
            (js/RegExp. "(%[0-9a-fA-F][0-9a-fA-F]|[^a-z0-9])+", "g") "-"))
(defn swap-trim  [[a b]] [(string/trim b) (string/trim a)])


;; ## integers / colors
(defn hex-color [n] (str "#" (.slice (.toString (bit-or 0x1000000 (bit-and 0xffffff n)) 16) 1)))

;; ## unique id
(def -unique-id-counter  (atom 0))
(defn unique-id  []  (str "id"  (swap! -unique-id-counter inc)))

;; ## transit
;(def -writer  (transit/writer :json))
;(def -reader  (transit/reader :json))
