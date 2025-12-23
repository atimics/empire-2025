(ns empire.menus
  (:require [empire.atoms :as atoms]
            [quil.core :as q]))

(defn find-menu-item
  "Finds the menu item index based on mouse position."
  [menu-x menu-y x y items]
  (when (and (>= x menu-x) (< x (+ menu-x 150)))
    (first (filter #(let [item-y (+ menu-y 45 (* % 20))]
                      (and (>= y (- item-y 11)) (< y (+ item-y 3))))
                   (range (count items))))))

(defn dismiss-existing-menu
  "Dismisses the menu if clicking outside it."
  [x y]
  (when (:visible @atoms/menu-state)
    (let [menu @atoms/menu-state
          menu-x (:x menu)
          menu-y (:y menu)
          items (:items menu)
          menu-width 150
          menu-height (+ 45 (* (count items) 20))]
      (when-not (and (>= x menu-x) (< x (+ menu-x menu-width))
                      (>= y menu-y) (< y (+ menu-y menu-height)))
        (swap! atoms/menu-state assoc :visible false)))))

(defn handle-menu-click
  "Handles clicking on menu items. Returns true if a menu item was clicked."
  [x y]
  (when (:visible @atoms/menu-state)
    (let [menu @atoms/menu-state
          menu-x (:x menu)
          menu-y (:y menu)
          items (:items menu)
          clicked-item-idx (find-menu-item menu-x menu-y x y items)]
      (when clicked-item-idx
        (let [item (nth items clicked-item-idx)
              [cell-x cell-y] @atoms/last-clicked-cell]
          (reset! atoms/last-clicked-item item)
          (println "Clicked on item:" item "at cell" cell-x "," cell-y))
        (swap! atoms/menu-state assoc :visible false)
        true))))

(defn draw-menu
  "Draws the menu if it's visible."
  []
  (when (:visible @atoms/menu-state)
    (let [{:keys [x y header items]} @atoms/menu-state
          item-height 20
          menu-width 150
          menu-height (+ 45 (* (count items) item-height))
          mouse-x (q/mouse-x)
          mouse-y (q/mouse-y)
          highlighted-idx (find-menu-item x y mouse-x mouse-y items)]
      (q/fill 200 200 200 200)
      (q/rect x y menu-width menu-height)
      ;; Header
      (q/fill 0)
      (q/text-font (q/create-font "CourierNewPS-BoldMT" 16 true))
      (q/text header (+ x 10) (+ y 20))
      ;; Line
      (q/stroke 0)
      (q/line (+ x 5) (+ y 25) (- (+ x menu-width) 5) (+ y 25))
      ;; Items
      (q/text-font (q/create-font "Courier New" 14))
      (doseq [[idx item] (map-indexed vector items)]
        (if (= idx highlighted-idx)
          (q/fill 255)
          (q/fill 0))
        (q/text item (+ x 10) (+ y 45 (* idx item-height)))))))