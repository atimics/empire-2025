use wasm_bindgen::JsCast;
use web_sys::{CanvasRenderingContext2d, HtmlCanvasElement};

use crate::protocol::CellMsg;
use crate::state::GameState;

// --- Cell dimensions (14x20 for better readability) ---
pub const CELL_W: f64 = 14.0;
pub const CELL_H: f64 = 20.0;

// Message area layout
const TEXT_AREA_ROWS: u32 = 3;
const TEXT_AREA_GAP: f64 = 8.0;
const MSG_LEFT_PADDING: f64 = 12.0;
const MSG_LINE_1_Y: f64 = 14.0;
const MSG_LINE_2_Y: f64 = 32.0;
const MSG_LINE_3_Y: f64 = 50.0;
const MSG_SEPARATOR_OFFSET: f64 = 4.0;

// Display area width fractions
const GAME_INFO_WIDTH_FRACTION: f64 = 0.375;
const DEBUG_WIDTH_FRACTION: f64 = 0.25;

// Minimum canvas width so UI elements (tutorial overlay, message area) have room
const MIN_CANVAS_W: f64 = 700.0;

// Cell character offsets within cells
const CELL_CHAR_X_OFFSET: f64 = 3.0;
const CELL_CHAR_Y_OFFSET: f64 = 15.0;

// --- Dark Cartographic color palette ---

// Terrain
const COLOR_PLAYER_CITY: [u8; 3] = [46, 160, 67];
const COLOR_COMPUTER_CITY: [u8; 3] = [218, 54, 51];
const COLOR_FREE_CITY: [u8; 3] = [201, 209, 217];
const COLOR_UNEXPLORED: [u8; 3] = [13, 17, 23];
const COLOR_LAND: [u8; 3] = [61, 43, 31];
const COLOR_SEA: [u8; 3] = [26, 58, 92];

const LAND_COLORS: [[u8; 3]; 8] = [
    [61, 43, 31],    // dark earth
    [74, 55, 40],    // warm earth
    [51, 36, 25],    // deep soil
    [82, 62, 45],    // clay
    [43, 30, 20],    // dark wood
    [92, 74, 54],    // sandstone
    [72, 53, 32],    // bark
    [58, 47, 38],    // taupe
];

// Units
const COLOR_AWAKE: [u8; 3] = [240, 246, 252];
const COLOR_SLEEPING: [u8; 3] = [72, 79, 88];
const COLOR_SENTRY: [u8; 3] = [240, 136, 62];
const COLOR_EXPLORE: [u8; 3] = [86, 211, 100];
const COLOR_PRODUCTION: [u8; 3] = [110, 118, 129];
const COLOR_WAYPOINT: [u8; 3] = [63, 185, 80];

// UI chrome
const COLOR_CANVAS_BG: [u8; 3] = [13, 17, 23];
const COLOR_PANEL_BG: [u8; 3] = [22, 27, 34];
const COLOR_PANEL_BORDER: [u8; 3] = [48, 54, 61];
const COLOR_TEXT_PRIMARY: [u8; 3] = [230, 237, 243];
const COLOR_TEXT_SECONDARY: [u8; 3] = [139, 148, 158];
const COLOR_ERROR: [u8; 3] = [248, 81, 73];
const COLOR_DEBUG: [u8; 3] = [121, 192, 255];
const COLOR_ACCENT: [u8; 3] = [88, 166, 255];
const COLOR_HOVER_BG: [u8; 3] = [31, 111, 235];

// Fog texture colors (subtle checkerboard for unexplored)
const FOG_LIGHT: [u8; 3] = [15, 19, 25];
const FOG_DARK: [u8; 3] = [11, 15, 21];

// --- Font strings ---
const FONT_CELL: &str = "bold 14px 'JetBrains Mono', 'Fira Code', 'Courier New', monospace";
const FONT_MSG: &str = "500 15px 'JetBrains Mono', 'Fira Code', 'Courier New', monospace";
const FONT_MENU_TITLE: &str = "700 18px 'JetBrains Mono', 'Fira Code', 'Courier New', monospace";
const FONT_MENU_ITEM: &str = "400 15px 'JetBrains Mono', 'Fira Code', 'Courier New', monospace";
const FONT_MENU_HINT: &str = "400 12px 'JetBrains Mono', 'Fira Code', 'Courier New', monospace";

// Production costs (rounds) per unit type
fn item_cost(unit_type: &str) -> u32 {
    match unit_type {
        "army" => 5,
        "fighter" => 10,
        "transport" => 30,
        "carrier" => 30,
        "patrol-boat" => 15,
        "destroyer" => 20,
        "submarine" => 20,
        "battleship" => 40,
        "satellite" => 50,
        _ => 10,
    }
}

fn unit_char(unit_type: &str) -> &str {
    match unit_type {
        "army" => "A",
        "fighter" => "F",
        "transport" => "T",
        "carrier" => "C",
        "destroyer" => "D",
        "submarine" => "S",
        "patrol-boat" => "P",
        "battleship" => "B",
        "satellite" => "Z",
        _ => "?",
    }
}

pub struct Renderer {
    ctx: CanvasRenderingContext2d,
}

impl Renderer {
    pub fn new(canvas: &HtmlCanvasElement) -> Result<Self, wasm_bindgen::JsValue> {
        let ctx = canvas
            .get_context("2d")?
            .unwrap()
            .dyn_into::<CanvasRenderingContext2d>()?;
        Ok(Self { ctx })
    }

    pub fn render(&mut self, state: &GameState, now: f64, canvas: &HtmlCanvasElement) {
        let (cols, rows) = state.map_size;
        if cols == 0 || rows == 0 {
            return;
        }

        let grid_w = cols as f64 * CELL_W;
        let map_h = rows as f64 * CELL_H;
        let canvas_w = grid_w.max(MIN_CANVAS_W);
        let text_h = TEXT_AREA_GAP + (TEXT_AREA_ROWS as f64 * CELL_H);
        let total_w = canvas_w as u32;
        let total_h = (map_h + text_h) as u32;

        // Resize canvas if needed
        if canvas.width() != total_w || canvas.height() != total_h {
            canvas.set_width(total_w);
            canvas.set_height(total_h);
        }

        // Clear with canvas background
        self.ctx.set_fill_style_str(&rgb(COLOR_CANVAS_BG));
        self.ctx.fill_rect(0.0, 0.0, total_w as f64, total_h as f64);

        // Smooth animation values
        let pulse_attention = pulse(now, 1500.0);
        let pulse_completed = pulse(now, 2000.0);
        let pulse_unit = pulse(now, 800.0);

        // Draw fog texture for unexplored cells
        self.draw_fog_texture(cols, rows);

        // Draw cell backgrounds with smooth pulse animations
        self.draw_cell_backgrounds(state, cols, rows, pulse_attention, pulse_completed);

        // Draw cell depth effect (highlight/shadow lines)
        self.draw_cell_depth(state, cols, rows);

        // Draw grid lines (soft)
        self.draw_grid(cols, rows, grid_w, map_h);

        // Draw hover cell highlight
        self.draw_hover_highlight(state, grid_w, map_h);

        // Draw production indicators, units, and waypoints
        self.ctx.set_font(FONT_CELL);
        self.draw_cell_contents(state, cols, rows, pulse_unit);

        // Draw message area panel
        self.draw_message_area(state, now, canvas_w, map_h, text_h);

        // Draw tutorial overlay if active and visible
        if let Some(ref tut) = state.tutorial {
            if tut.overlay_visible {
                self.draw_tutorial_overlay(tut, canvas_w);
            }
        }

        // Draw contextual help overlay (tips + cheat sheet)
        if state.show_help_overlay {
            self.draw_help_overlay(state, canvas_w);
        }

        // Draw tutorial menu if open
        if let Some(ref menu) = state.tutorial_menu {
            self.draw_tutorial_menu(menu, state.tutorial_menu_hovered, total_w as f64, total_h as f64);
        }

        // Draw load menu if open
        if let Some(ref menu) = state.load_menu {
            self.draw_load_menu(menu, total_w as f64, total_h as f64);
        }
    }

    fn draw_fog_texture(&self, cols: usize, rows: usize) {
        for col in 0..cols {
            for row in 0..rows {
                let color = if (col + row) % 2 == 0 { FOG_LIGHT } else { FOG_DARK };
                self.ctx.set_fill_style_str(&rgb(color));
                self.ctx.fill_rect(
                    col as f64 * CELL_W,
                    row as f64 * CELL_H,
                    CELL_W,
                    CELL_H,
                );
            }
        }
    }

    fn draw_cell_backgrounds(
        &self,
        state: &GameState,
        cols: usize,
        rows: usize,
        pulse_attention: f64,
        pulse_completed: f64,
    ) {
        let attention_cell = state.attention_coords.first();

        for col in 0..cols {
            for row in 0..rows {
                let cell = match state.cells.get(col).and_then(|c| c.get(row)) {
                    Some(Some(cell)) => cell,
                    _ => continue,
                };

                if cell.t == "unexplored" {
                    continue; // fog texture already drawn
                }

                let base_color = cell_color(cell);
                let is_attention = attention_cell.map_or(false, |ac| ac.0 == col && ac.1 == row);

                let is_completed = cell.t == "city"
                    && cell.cs.as_deref() != Some("free")
                    && cell.prod.as_ref().map_or(false, |p| p.remaining == 0);

                let color = if is_attention {
                    // Smooth pulse between base color and bright highlight
                    let highlight = brighten(base_color, 1.6);
                    lerp_color(base_color, highlight, pulse_attention * 0.7)
                } else if is_completed {
                    // Smooth pulse between city color and white
                    lerp_color(base_color, COLOR_TEXT_PRIMARY, pulse_completed * 0.5)
                } else {
                    base_color
                };

                self.ctx.set_fill_style_str(&rgb(color));
                self.ctx.fill_rect(
                    col as f64 * CELL_W,
                    row as f64 * CELL_H,
                    CELL_W,
                    CELL_H,
                );
            }
        }
    }

    fn draw_cell_depth(&self, state: &GameState, cols: usize, rows: usize) {
        for col in 0..cols {
            for row in 0..rows {
                let cell = match state.cells.get(col).and_then(|c| c.get(row)) {
                    Some(Some(cell)) => cell,
                    _ => continue,
                };

                if cell.t == "unexplored" {
                    continue;
                }

                let base = cell_color(cell);
                let x = col as f64 * CELL_W;
                let y = row as f64 * CELL_H;

                // Top highlight
                let highlight = brighten(base, 1.25);
                self.ctx.set_stroke_style_str(&rgba(highlight, 0.4));
                self.ctx.begin_path();
                self.ctx.move_to(x + 0.5, y + 0.5);
                self.ctx.line_to(x + CELL_W - 0.5, y + 0.5);
                self.ctx.stroke();

                // Bottom shadow
                let shadow = darken(base, 0.6);
                self.ctx.set_stroke_style_str(&rgba(shadow, 0.4));
                self.ctx.begin_path();
                self.ctx.move_to(x + 0.5, y + CELL_H - 0.5);
                self.ctx.line_to(x + CELL_W - 0.5, y + CELL_H - 0.5);
                self.ctx.stroke();
            }
        }
    }

    fn draw_grid(&self, cols: usize, rows: usize, map_w: f64, map_h: f64) {
        self.ctx.set_stroke_style_str("rgba(0,0,0,0.3)");
        self.ctx.set_line_width(1.0);
        self.ctx.begin_path();
        for col in 0..=cols {
            let x = col as f64 * CELL_W;
            self.ctx.move_to(x, 0.0);
            self.ctx.line_to(x, map_h);
        }
        for row in 0..=rows {
            let y = row as f64 * CELL_H;
            self.ctx.move_to(0.0, y);
            self.ctx.line_to(map_w, y);
        }
        self.ctx.stroke();
    }

    fn draw_hover_highlight(&self, state: &GameState, map_w: f64, map_h: f64) {
        if let (Some(col), Some(row)) = (state.hover_col, state.hover_row) {
            let (cols, rows) = state.map_size;
            if col < cols && row < rows {
                let x = col as f64 * CELL_W;
                let y = row as f64 * CELL_H;
                if x + CELL_W <= map_w && y + CELL_H <= map_h {
                    self.ctx.set_stroke_style_str(&rgba(COLOR_ACCENT, 0.8));
                    self.ctx.set_line_width(2.0);
                    self.ctx.stroke_rect(x + 0.5, y + 0.5, CELL_W - 1.0, CELL_H - 1.0);
                    self.ctx.set_line_width(1.0);
                }
            }
        }
    }

    fn draw_cell_contents(
        &self,
        state: &GameState,
        cols: usize,
        rows: usize,
        pulse_unit: f64,
    ) {
        let attention_cell = state.attention_coords.first();

        for col in 0..cols {
            for row in 0..rows {
                let cell = match state.cells.get(col).and_then(|c| c.get(row)) {
                    Some(Some(cell)) => cell,
                    _ => continue,
                };

                let cx = col as f64 * CELL_W + CELL_CHAR_X_OFFSET;
                let cy = row as f64 * CELL_H + CELL_CHAR_Y_OFFSET;

                // Draw production indicator (thermometer bar + character)
                if cell.t == "city" {
                    if let Some(ref prod) = cell.prod {
                        let total = item_cost(&prod.item) as f64;
                        let remaining = prod.remaining as f64;
                        let progress = (total - remaining) / total;

                        if progress > 0.0 && remaining > 0.0 {
                            let base = cell_color(cell);
                            let dark = darken(base, 0.5);
                            self.ctx.set_global_alpha(0.5);
                            self.ctx.set_fill_style_str(&rgb(dark));
                            let bar_height = CELL_H * progress;
                            self.ctx.fill_rect(
                                col as f64 * CELL_W,
                                row as f64 * CELL_H + (CELL_H - bar_height),
                                CELL_W,
                                bar_height,
                            );
                            self.ctx.set_global_alpha(1.0);
                        }

                        draw_text_shadow(&self.ctx, unit_char(&prod.item), cx, cy, COLOR_PRODUCTION);
                    }
                }

                // Draw unit
                if let Some(ref unit) = cell.u {
                    let is_attention = attention_cell.map_or(false, |ac| ac.0 == col && ac.1 == row);

                    let has_awake_airport = cell.af.unwrap_or(0) > 0;
                    let has_awake_carrier = unit.t == "carrier"
                        && cell.af.unwrap_or(0) > 0;
                    let has_awake_army = unit.t == "transport"
                        && cell.aa.unwrap_or(0) > 0;
                    let has_contained = has_awake_airport || has_awake_carrier || has_awake_army;

                    if is_attention && has_contained {
                        // Smooth crossfade between container and contained unit
                        let contained_ch = if has_awake_airport || has_awake_carrier {
                            "F"
                        } else {
                            "A"
                        };
                        let container_color = unit_color(unit);
                        let container_ch = unit_char(&unit.t);
                        let container_display = if unit.o == "computer" {
                            container_ch.to_lowercase()
                        } else {
                            container_ch.to_string()
                        };

                        let alpha_contained = pulse_unit;
                        let alpha_container = 1.0 - pulse_unit;

                        if alpha_container > 0.05 {
                            self.ctx.set_global_alpha(alpha_container);
                            draw_text_shadow(&self.ctx, &container_display, cx, cy, container_color);
                        }
                        if alpha_contained > 0.05 {
                            self.ctx.set_global_alpha(alpha_contained);
                            draw_text_shadow(&self.ctx, contained_ch, cx, cy, COLOR_AWAKE);
                        }
                        self.ctx.set_global_alpha(1.0);
                    } else {
                        let color = unit_color(unit);
                        let ch = unit_char(&unit.t);
                        let display_ch = if unit.o == "computer" {
                            ch.to_lowercase()
                        } else {
                            ch.to_string()
                        };
                        draw_text_shadow(&self.ctx, &display_ch, cx, cy, color);
                    }
                } else if cell.wp == Some(true) {
                    draw_text_shadow(&self.ctx, "*", cx, cy, COLOR_WAYPOINT);
                }
            }
        }
    }

    fn draw_message_area(
        &self,
        state: &GameState,
        _now: f64,
        map_w: f64,
        map_h: f64,
        _text_h: f64,
    ) {
        let text_x = 0.0;
        let text_y = map_h + TEXT_AREA_GAP;
        let text_w = map_w;
        let info_w = text_w * GAME_INFO_WIDTH_FRACTION;
        let debug_w = text_w * DEBUG_WIDTH_FRACTION;
        let debug_x = text_x + info_w;
        let right_edge = text_x + text_w;
        let panel_h = TEXT_AREA_ROWS as f64 * CELL_H;

        // Panel background
        self.ctx.set_fill_style_str(&rgb(COLOR_PANEL_BG));
        self.ctx.fill_rect(text_x, text_y - MSG_SEPARATOR_OFFSET, text_w, panel_h + MSG_SEPARATOR_OFFSET);

        // Top separator line
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.begin_path();
        self.ctx.move_to(text_x, text_y - MSG_SEPARATOR_OFFSET);
        self.ctx.line_to(text_x + text_w, text_y - MSG_SEPARATOR_OFFSET);
        self.ctx.stroke();

        // Section dividers
        self.ctx.begin_path();
        self.ctx.move_to(debug_x, text_y - MSG_SEPARATOR_OFFSET);
        self.ctx.line_to(debug_x, text_y + panel_h);
        self.ctx.move_to(debug_x + debug_w, text_y - MSG_SEPARATOR_OFFSET);
        self.ctx.line_to(debug_x + debug_w, text_y + panel_h);
        self.ctx.stroke();

        self.ctx.set_font(FONT_MSG);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        self.ctx.set_text_baseline("top");

        // --- Game Info region (left) ---
        if !state.attention_message.is_empty() {
            self.ctx.fill_text(
                &state.attention_message,
                text_x + MSG_LEFT_PADDING,
                text_y + MSG_LINE_1_Y,
            ).ok();
        }

        if !state.turn_message.is_empty() {
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
            self.ctx.fill_text(
                &state.turn_message,
                text_x + MSG_LEFT_PADDING,
                text_y + MSG_LINE_2_Y,
            ).ok();
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        } else if let Some(dest) = state.destination {
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
            self.ctx.fill_text(
                &format!("Dest: {},{}", dest.0, dest.1),
                text_x + MSG_LEFT_PADDING,
                text_y + MSG_LINE_2_Y,
            ).ok();
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        }

        // Error notification banner with fade
        if !state.error_message.is_empty() {
            let now_ms = js_sys::Date::now();
            if now_ms < state.error_until {
                let remaining = state.error_until - now_ms;
                let total_duration = 10000.0;
                let elapsed_frac = 1.0 - (remaining / total_duration);

                let alpha = if elapsed_frac > 0.8 {
                    1.0 - (elapsed_frac - 0.8) / 0.2
                } else {
                    1.0
                };

                if alpha > 0.01 {
                    let err_y = text_y + MSG_LINE_3_Y - 4.0;
                    let err_h = 22.0;

                    self.ctx.set_global_alpha(alpha * 0.1);
                    self.ctx.set_fill_style_str(&rgb(COLOR_ERROR));
                    self.ctx.fill_rect(text_x, err_y, info_w, err_h);

                    self.ctx.set_global_alpha(alpha);
                    self.ctx.set_fill_style_str(&rgb(COLOR_ERROR));
                    self.ctx.fill_rect(text_x, err_y, 3.0, err_h);

                    self.ctx.fill_text(
                        &state.error_message,
                        text_x + MSG_LEFT_PADDING,
                        text_y + MSG_LINE_3_Y,
                    ).ok();
                    self.ctx.set_global_alpha(1.0);
                }
            }
        }

        // --- Debug region (center) ---
        if !state.debug_message.is_empty() {
            self.ctx.set_fill_style_str(&rgb(COLOR_DEBUG));
            let lines: Vec<&str> = state.debug_message.split('\n').collect();
            let y_offsets = [MSG_LINE_1_Y, MSG_LINE_2_Y, MSG_LINE_3_Y];
            let center_x = debug_x + debug_w / 2.0;
            for (line, y_off) in lines.iter().take(3).zip(y_offsets.iter()) {
                if let Ok(metrics) = self.ctx.measure_text(line) {
                    let msg_x = center_x - metrics.width() / 2.0;
                    self.ctx.fill_text(line, msg_x, text_y + y_off).ok();
                }
            }
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        }

        // --- Game Status region (right) ---
        self.ctx.set_text_align("right");

        let round_str = format!("Round: {}", state.round);
        if state.paused || state.pause_requested {
            let full_str = format!("PAUSED  {}", round_str);
            let full_width = self.ctx.measure_text(&full_str).map(|m| m.width()).unwrap_or(0.0);
            let x = right_edge - full_width - MSG_LEFT_PADDING;
            self.ctx.set_text_align("left");
            self.ctx.set_fill_style_str(&rgb(COLOR_ERROR));
            self.ctx.fill_text("PAUSED  ", x, text_y + MSG_LINE_1_Y).ok();
            let paused_width = self.ctx.measure_text("PAUSED  ").map(|m| m.width()).unwrap_or(0.0);
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
            self.ctx.fill_text(&round_str, x + paused_width, text_y + MSG_LINE_1_Y).ok();
            self.ctx.set_text_align("right");
        } else {
            self.ctx.fill_text(&round_str, right_edge - MSG_LEFT_PADDING, text_y + MSG_LINE_1_Y).ok();
        }

        if !state.hover_message.is_empty() {
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
            self.ctx.fill_text(&state.hover_message, right_edge - MSG_LEFT_PADDING, text_y + MSG_LINE_2_Y).ok();
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        }

        if !state.production_status.is_empty() {
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
            self.ctx.fill_text(&state.production_status, right_edge - MSG_LEFT_PADDING, text_y + MSG_LINE_3_Y).ok();
        }

        self.ctx.set_text_align("left");
        self.ctx.set_text_baseline("alphabetic");
    }

    fn draw_tutorial_overlay(
        &self,
        tut: &crate::protocol::TutorialMsg,
        canvas_w: f64,
    ) {
        let padding = 16.0;
        let panel_w = 380.0f64.min(canvas_w * 0.50);
        let right_margin = 12.0;
        let top_margin = 12.0;
        let left = canvas_w - panel_w - right_margin;
        let top = top_margin;
        let content_w = panel_w - 2.0 * padding - 4.0;

        // Set page text font before measuring for word-wrap
        self.ctx.set_font(FONT_MENU_ITEM);
        let lines = self.wrap_text(&tut.page_text, content_w);

        // Measure content height
        let title_h = 24.0;
        let line_h = 20.0;
        let text_h = lines.len() as f64 * line_h;
        let nav_h = 28.0;
        let panel_h = padding + title_h + 8.0 + text_h + 12.0 + nav_h + padding;

        // Panel background with transparency
        self.ctx.set_global_alpha(0.92);
        self.ctx.set_fill_style_str(&rgb(COLOR_PANEL_BG));
        self.ctx.fill_rect(left, top, panel_w, panel_h);
        self.ctx.set_global_alpha(1.0);

        // Accent border (left edge)
        self.ctx.set_fill_style_str(&rgb(COLOR_ACCENT));
        self.ctx.fill_rect(left, top, 3.0, panel_h);

        // Border
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.set_line_width(1.0);
        self.ctx.stroke_rect(left, top, panel_w, panel_h);

        // Title
        self.ctx.set_font(FONT_MENU_TITLE);
        self.ctx.set_fill_style_str(&rgb(COLOR_ACCENT));
        self.ctx.set_text_baseline("top");
        self.ctx.fill_text(
            &tut.scenario_name,
            left + padding + 4.0,
            top + padding,
        ).ok();

        // Separator under title
        let sep_y = top + padding + title_h;
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.begin_path();
        self.ctx.move_to(left + padding, sep_y);
        self.ctx.line_to(left + panel_w - padding, sep_y);
        self.ctx.stroke();

        // Page text (word-wrapped, 15px font)
        self.ctx.set_font(FONT_MENU_ITEM);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        let text_top = sep_y + 8.0;
        for (i, line) in lines.iter().enumerate() {
            self.ctx.fill_text(
                line,
                left + padding + 4.0,
                text_top + i as f64 * line_h,
            ).ok();
        }

        // Navigation hints
        let nav_y = text_top + text_h + 12.0;
        self.ctx.set_font(FONT_MENU_HINT);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
        let page_str = format!(
            "Page {}/{}  [N]ext [B]ack [ESC]hide",
            tut.page_index + 1,
            tut.page_count,
        );
        self.ctx.fill_text(&page_str, left + padding + 4.0, nav_y).ok();

        self.ctx.set_text_baseline("alphabetic");
    }

    fn compute_controls_lines(&self, state: &GameState) -> Vec<String> {
        // Keep this small and action-oriented. 3-6 lines max.
        let mut lines: Vec<String> = Vec::new();

        // Global-ish controls
        let mut globals: Vec<&str> = Vec::new();
        globals.push("P pause");
        globals.push("+ map");
        globals.push("? tutorial");
        globals.push("h tips on/off");
        if !globals.is_empty() {
            lines.push(format!("Keys: {}", globals.join("  ")));
        }

        // Contextual controls
        if state.load_menu.is_some() {
            lines.push("Load menu: click a file, ESC closes".to_string());
            return lines;
        }
        if state.tutorial_menu.is_some() {
            lines.push("Tutorial menu: click a scenario, ESC closes".to_string());
            return lines;
        }
        if let Some(ref tut) = state.tutorial {
            if tut.overlay_visible {
                lines.push("Tutorial: N next, B back, ESC hide".to_string());
            } else {
                lines.push("Tutorial: ESC show overlay".to_string());
            }
        }

        if state.waiting_for_input {
            if let Some(&(col, row)) = state.attention_coords.first() {
                // Try to infer what kind of attention is needed from the cell.
                let cell = state.cells.get(col).and_then(|c| c.get(row)).and_then(|c| c.clone());
                if let Some(cell) = cell {
                    if cell.cs.as_deref() == Some("player") && cell.t == "city" && cell.u.is_none() {
                        lines.push("Now: choose production (F/T/P/D/S/C/B/Z), X none, SPACE skip".to_string());
                    } else {
                        lines.push("Now: move QWEASDZXC, SPACE skip, U unload/wake, S sentry, L explore".to_string());
                    }
                } else {
                    lines.push("Now: act on the highlighted item".to_string());
                }
            }
        } else if state.paused || state.pause_requested {
            lines.push("Now: SPACE steps one round".to_string());
        }

        // “New mechanic” nudges (only when relevant and not yet used)
        let mut nudges: Vec<&str> = Vec::new();
        if !state.used_map_cycle {
            nudges.push("+ shows enemy/actual map");
        }
        if !state.used_tutorial_menu {
            nudges.push("? opens tutorial menu");
        }
        if !state.used_pause {
            nudges.push("P pauses at round end");
        }
        // Only nudge destination/waypoint once we can aim them (hover-based)
        if state.hover_col.is_some() && state.hover_row.is_some() {
            if !state.used_destination {
                nudges.push(". sets destination at hover");
            }
            if !state.used_waypoint {
                nudges.push("* toggles waypoint at hover");
            }
        }
        if !nudges.is_empty() {
            lines.push(format!("New: {}", nudges.join("  ")));
        }

        lines
    }

    fn draw_help_overlay(
        &self,
        state: &GameState,
        canvas_w: f64,
    ) {
        let padding = 14.0;
        let panel_w = 360.0f64.min(canvas_w * 0.45);
        let left_margin = 12.0;
        let top_margin = 12.0;
        let left = left_margin;
        let top = top_margin;
        let content_w = panel_w - 2.0 * padding - 4.0;

        let tip_title = state
            .tips
            .as_ref()
            .map(|t| t.title.as_str())
            .unwrap_or("Tip");
        let tip_text = state
            .tips
            .as_ref()
            .map(|t| t.text.as_str())
            .unwrap_or("Use [H] to hide this panel.");

        // Wrap both tip + controls
        self.ctx.set_font(FONT_MENU_ITEM);
        let tip_lines = self.wrap_text(tip_text, content_w);
        let controls_lines = self.compute_controls_lines(state);
        let mut wrapped_controls: Vec<String> = Vec::new();
        for line in controls_lines {
            for w in self.wrap_text(&line, content_w) {
                wrapped_controls.push(w);
            }
        }

        // Measure
        let title_h = 20.0;
        let section_gap = 10.0;
        let line_h = 18.0;
        let tip_h = (tip_lines.len().max(1) as f64) * line_h;
        let controls_h = (wrapped_controls.len().max(1) as f64) * line_h;
        let hint_h = 18.0;
        let panel_h = padding
            + title_h
            + 8.0
            + tip_h
            + section_gap
            + controls_h
            + 10.0
            + hint_h
            + padding;

        // Panel background
        self.ctx.set_global_alpha(0.90);
        self.ctx.set_fill_style_str(&rgb(COLOR_PANEL_BG));
        self.ctx.fill_rect(left, top, panel_w, panel_h);
        self.ctx.set_global_alpha(1.0);

        // Accent
        self.ctx.set_fill_style_str(&rgb(COLOR_ACCENT));
        self.ctx.fill_rect(left, top, 3.0, panel_h);

        // Border
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.set_line_width(1.0);
        self.ctx.stroke_rect(left, top, panel_w, panel_h);

        // Title
        self.ctx.set_font(FONT_MENU_TITLE);
        self.ctx.set_fill_style_str(&rgb(COLOR_ACCENT));
        self.ctx.set_text_baseline("top");
        self.ctx.fill_text(tip_title, left + padding + 4.0, top + padding).ok();

        // Separator
        let sep_y = top + padding + title_h;
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.begin_path();
        self.ctx.move_to(left + padding, sep_y);
        self.ctx.line_to(left + panel_w - padding, sep_y);
        self.ctx.stroke();

        // Tip text
        self.ctx.set_font(FONT_MENU_ITEM);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        let text_top = sep_y + 8.0;
        for (i, line) in tip_lines.iter().enumerate() {
            self.ctx
                .fill_text(line, left + padding + 4.0, text_top + i as f64 * line_h)
                .ok();
        }

        // Controls section
        let controls_top = text_top + tip_h + section_gap;
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
        for (i, line) in wrapped_controls.iter().enumerate() {
            self.ctx
                .fill_text(line, left + padding + 4.0, controls_top + i as f64 * line_h)
                .ok();
        }

        // Hint
        let hint_y = controls_top + controls_h + 10.0;
        self.ctx.set_font(FONT_MENU_HINT);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
        self.ctx
            .fill_text("[H] hide panel", left + padding + 4.0, hint_y)
            .ok();

        self.ctx.set_text_baseline("alphabetic");
    }

    fn draw_tutorial_menu(
        &self,
        menu: &crate::protocol::TutorialMenuMsg,
        hovered: Option<usize>,
        screen_w: f64,
        screen_h: f64,
    ) {
        let scenario_count = menu.scenarios.len();
        let padding = 24.0;
        let item_height = 48.0;
        let title_height = 40.0;
        let hint_height = 30.0;
        let menu_w = 520.0f64.min(screen_w * 0.85);
        let menu_h = (title_height + padding + scenario_count as f64 * item_height + hint_height + padding)
            .min(screen_h * 0.85);
        let left = (screen_w - menu_w) / 2.0;
        let top = (screen_h - menu_h) / 2.0;
        let content_top = top + padding + title_height;

        // Dark overlay
        self.ctx.set_global_alpha(0.85);
        self.ctx.set_fill_style_str(&rgb(COLOR_CANVAS_BG));
        self.ctx.fill_rect(0.0, 0.0, screen_w, screen_h);
        self.ctx.set_global_alpha(1.0);

        // Dialog shadow
        self.ctx.set_fill_style_str("rgba(0,0,0,0.3)");
        self.ctx.fill_rect(left + 4.0, top + 4.0, menu_w, menu_h);

        // Dialog background
        self.ctx.set_fill_style_str(&rgb(COLOR_PANEL_BG));
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.set_line_width(1.0);
        self.ctx.fill_rect(left, top, menu_w, menu_h);
        self.ctx.stroke_rect(left, top, menu_w, menu_h);

        // Title
        self.ctx.set_font(FONT_MENU_TITLE);
        self.ctx.set_fill_style_str(&rgb(COLOR_ACCENT));
        self.ctx.set_text_baseline("top");
        self.ctx.fill_text("Tutorial Scenarios", left + padding, top + padding).ok();

        // Title separator
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.begin_path();
        self.ctx.move_to(left + padding, content_top - 4.0);
        self.ctx.line_to(left + menu_w - padding, content_top - 4.0);
        self.ctx.stroke();

        // Scenario list
        for (idx, scenario) in menu.scenarios.iter().enumerate() {
            let y = content_top + idx as f64 * item_height;
            if hovered == Some(idx) {
                self.ctx.set_fill_style_str(&rgb(COLOR_HOVER_BG));
                self.ctx.fill_rect(left + 1.0, y, menu_w - 2.0, item_height);
            }

            // Scenario number + name
            let name_color = if hovered == Some(idx) { [255, 255, 255] } else { COLOR_TEXT_PRIMARY };
            self.ctx.set_font(FONT_MENU_ITEM);
            self.ctx.set_fill_style_str(&rgb(name_color));
            let label = format!("{}. {}", idx + 1, scenario.name);
            self.ctx.fill_text(&label, left + padding, y + 8.0).ok();

            // Description
            let desc_color = if hovered == Some(idx) { [200, 210, 220] } else { COLOR_TEXT_SECONDARY };
            self.ctx.set_font(FONT_MENU_HINT);
            self.ctx.set_fill_style_str(&rgb(desc_color));
            self.ctx.fill_text(&scenario.description, left + padding + 20.0, y + 28.0).ok();
        }

        // Hint
        self.ctx.set_font(FONT_MENU_HINT);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
        self.ctx.fill_text(
            "Click to start  |  Press ESC to close",
            left + padding,
            top + menu_h - padding,
        ).ok();

        self.ctx.set_text_baseline("alphabetic");
    }

    fn draw_load_menu(
        &self,
        menu: &crate::protocol::LoadMenuMsg,
        screen_w: f64,
        screen_h: f64,
    ) {
        let file_count = menu.files.len();
        let padding = 24.0;
        let item_height = 36.0;
        let title_height = 40.0;
        let hint_height = 30.0;
        let menu_w = 500.0f64.min(screen_w * 0.8);
        let menu_h = (title_height + padding + file_count as f64 * item_height + hint_height + padding)
            .min(screen_h * 0.8);
        let left = (screen_w - menu_w) / 2.0;
        let top = (screen_h - menu_h) / 2.0;
        let content_top = top + padding + title_height;

        // Dark overlay
        self.ctx.set_global_alpha(0.85);
        self.ctx.set_fill_style_str(&rgb(COLOR_CANVAS_BG));
        self.ctx.fill_rect(0.0, 0.0, screen_w, screen_h);
        self.ctx.set_global_alpha(1.0);

        // Dialog shadow
        self.ctx.set_fill_style_str("rgba(0,0,0,0.3)");
        self.ctx.fill_rect(left + 4.0, top + 4.0, menu_w, menu_h);

        // Dialog background
        self.ctx.set_fill_style_str(&rgb(COLOR_PANEL_BG));
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.set_line_width(1.0);
        self.ctx.fill_rect(left, top, menu_w, menu_h);
        self.ctx.stroke_rect(left, top, menu_w, menu_h);

        // Title
        self.ctx.set_font(FONT_MENU_TITLE);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
        self.ctx.set_text_baseline("top");
        self.ctx.fill_text("Load Game", left + padding, top + padding).ok();

        // Title separator
        self.ctx.set_stroke_style_str(&rgb(COLOR_PANEL_BORDER));
        self.ctx.begin_path();
        self.ctx.move_to(left + padding, content_top - 4.0);
        self.ctx.line_to(left + menu_w - padding, content_top - 4.0);
        self.ctx.stroke();

        // File list
        self.ctx.set_font(FONT_MENU_ITEM);
        if menu.files.is_empty() {
            self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
            self.ctx.fill_text("No saved games found", left + padding, content_top + 10.0).ok();
        } else {
            for (idx, filename) in menu.files.iter().enumerate() {
                let y = content_top + idx as f64 * item_height;
                if menu.hovered == Some(idx) {
                    self.ctx.set_fill_style_str(&rgb(COLOR_HOVER_BG));
                    self.ctx.fill_rect(left + 1.0, y, menu_w - 2.0, item_height);
                    self.ctx.set_fill_style_str("white");
                    self.ctx.fill_text(filename, left + padding, y + 10.0).ok();
                } else {
                    self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_PRIMARY));
                    self.ctx.fill_text(filename, left + padding, y + 10.0).ok();
                }
            }
        }

        // ESC hint
        self.ctx.set_font(FONT_MENU_HINT);
        self.ctx.set_fill_style_str(&rgb(COLOR_TEXT_SECONDARY));
        self.ctx.fill_text("Press ESC to close", left + padding, top + menu_h - padding).ok();
        self.ctx.set_text_baseline("alphabetic");
    }

    /// Word-wrap text to fit within `max_width` pixels.
    /// Respects explicit newlines in the input. The current canvas font
    /// must be set before calling this method.
    fn wrap_text(&self, text: &str, max_width: f64) -> Vec<String> {
        let mut lines = Vec::new();
        for raw_line in text.split('\n') {
            if raw_line.is_empty() {
                lines.push(String::new());
                continue;
            }
            let words: Vec<&str> = raw_line.split(' ').collect();
            if words.is_empty() {
                lines.push(String::new());
                continue;
            }
            let mut current = String::from(words[0]);
            for word in &words[1..] {
                let test = format!("{} {}", current, word);
                let fits = self.ctx.measure_text(&test)
                    .map(|m| m.width() <= max_width)
                    .unwrap_or(true);
                if fits {
                    current = test;
                } else {
                    lines.push(current);
                    current = String::from(*word);
                }
            }
            lines.push(current);
        }
        lines
    }
}

// --- Helper functions ---

fn pulse(now: f64, period_ms: f64) -> f64 {
    0.5 + 0.5 * (now * 2.0 * std::f64::consts::PI / period_ms).sin()
}

fn lerp_color(a: [u8; 3], b: [u8; 3], t: f64) -> [u8; 3] {
    [
        (a[0] as f64 + (b[0] as f64 - a[0] as f64) * t) as u8,
        (a[1] as f64 + (b[1] as f64 - a[1] as f64) * t) as u8,
        (a[2] as f64 + (b[2] as f64 - a[2] as f64) * t) as u8,
    ]
}

fn brighten(color: [u8; 3], factor: f64) -> [u8; 3] {
    [
        (color[0] as f64 * factor).min(255.0) as u8,
        (color[1] as f64 * factor).min(255.0) as u8,
        (color[2] as f64 * factor).min(255.0) as u8,
    ]
}

fn darken(color: [u8; 3], factor: f64) -> [u8; 3] {
    [
        (color[0] as f64 * factor) as u8,
        (color[1] as f64 * factor) as u8,
        (color[2] as f64 * factor) as u8,
    ]
}

fn rgb(color: [u8; 3]) -> String {
    format!("rgb({},{},{})", color[0], color[1], color[2])
}

fn rgba(color: [u8; 3], alpha: f64) -> String {
    format!("rgba({},{},{},{})", color[0], color[1], color[2], alpha)
}

fn cell_color(cell: &CellMsg) -> [u8; 3] {
    match cell.t.as_str() {
        "city" => match cell.cs.as_deref() {
            Some("player") => COLOR_PLAYER_CITY,
            Some("computer") => COLOR_COMPUTER_CITY,
            Some("free") => COLOR_FREE_CITY,
            _ => COLOR_FREE_CITY,
        },
        "land" => {
            if let Some(cid) = cell.cid {
                LAND_COLORS[cid as usize % LAND_COLORS.len()]
            } else {
                COLOR_LAND
            }
        }
        "sea" => COLOR_SEA,
        _ => COLOR_UNEXPLORED,
    }
}

fn unit_color(unit: &crate::protocol::UnitMsg) -> [u8; 3] {
    if unit.o == "computer" && unit.t == "army" {
        return COLOR_AWAKE;
    }
    if unit.transport_mission.as_deref() == Some("loading") {
        return COLOR_SLEEPING;
    }
    match unit.m.as_str() {
        "awake" => COLOR_AWAKE,
        "sentry" => COLOR_SENTRY,
        "explore" | "coastline-follow" => COLOR_EXPLORE,
        _ => COLOR_SLEEPING,
    }
}

fn draw_text_shadow(ctx: &CanvasRenderingContext2d, text: &str, x: f64, y: f64, color: [u8; 3]) {
    ctx.set_fill_style_str("rgba(0,0,0,0.6)");
    ctx.fill_text(text, x + 1.0, y + 1.0).ok();
    ctx.set_fill_style_str(&rgb(color));
    ctx.fill_text(text, x, y).ok();
}
