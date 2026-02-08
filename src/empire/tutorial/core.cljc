(ns empire.tutorial.core
  (:require [empire.atoms :as atoms]
            [empire.tutorial.map-builder :as map-builder]
            [empire.tutorial.scenarios :as scenarios]))

(defn start-tutorial!
  "Starts a tutorial scenario by keyword id."
  [scenario-id]
  (when-let [scenario (scenarios/get-scenario scenario-id)]
    ;; Build the tutorial map
    (map-builder/build-tutorial-map! (:map-strings scenario))
    ;; Enable game-over check only for the battle scenario
    (when (= scenario-id :battle)
      (reset! atoms/game-over-check-enabled true))
    ;; Set tutorial state
    (reset! atoms/tutorial-active true)
    (reset! atoms/tutorial-scenario-id scenario-id)
    (reset! atoms/tutorial-pages (:pages scenario))
    (reset! atoms/tutorial-page-index 0)
    (reset! atoms/tutorial-overlay-visible true)
    ;; Close the menu
    (reset! atoms/tutorial-menu-open false)
    (reset! atoms/tutorial-menu-hovered nil)
    true))

(defn exit-tutorial!
  "Exits the current tutorial."
  []
  (reset! atoms/tutorial-active false)
  (reset! atoms/tutorial-scenario-id nil)
  (reset! atoms/tutorial-pages [])
  (reset! atoms/tutorial-page-index 0)
  (reset! atoms/tutorial-overlay-visible true))

(defn next-page!
  "Advances to the next tutorial page."
  []
  (let [idx @atoms/tutorial-page-index
        max-idx (dec (count @atoms/tutorial-pages))]
    (when (< idx max-idx)
      (reset! atoms/tutorial-page-index (inc idx))
      ;; Show overlay if hidden
      (reset! atoms/tutorial-overlay-visible true))))

(defn prev-page!
  "Goes back to the previous tutorial page."
  []
  (let [idx @atoms/tutorial-page-index]
    (when (pos? idx)
      (reset! atoms/tutorial-page-index (dec idx))
      (reset! atoms/tutorial-overlay-visible true))))

(defn toggle-overlay!
  "Toggles the tutorial overlay visibility."
  []
  (swap! atoms/tutorial-overlay-visible not))

(defn current-page-text
  "Returns the text of the current tutorial page, or nil."
  []
  (let [pages @atoms/tutorial-pages
        idx @atoms/tutorial-page-index]
    (get pages idx)))

(defn open-menu!
  "Opens the tutorial selection menu."
  []
  (reset! atoms/tutorial-scenarios-list (scenarios/scenarios-list))
  (reset! atoms/tutorial-menu-open true)
  (reset! atoms/tutorial-menu-hovered nil))

(defn close-menu!
  "Closes the tutorial selection menu."
  []
  (reset! atoms/tutorial-menu-open false)
  (reset! atoms/tutorial-menu-hovered nil))
