(ns empire.ui.coordinates
  "Pure coordinate conversion functions for screen/cell mapping.
   No Quil or atom dependencies - purely mathematical transformations.")

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Pure function - takes dimensions as parameters.
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  ;; The cell width is screen width / number of columns in a row (map-rows in our indexing)
  ;; The cell height is screen height / number of rows (map-cols in our indexing)
  ;; This matches the original behavior where variable names were swapped
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))
