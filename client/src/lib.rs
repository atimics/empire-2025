mod protocol;
mod renderer;
mod state;

use wasm_bindgen::prelude::*;
use wasm_bindgen::JsCast;
use web_sys::{WebSocket, MessageEvent, KeyboardEvent, MouseEvent, HtmlCanvasElement};
use std::cell::RefCell;
use std::rc::Rc;

use crate::state::GameState;
use crate::renderer::{Renderer, CELL_W, CELL_H};
use crate::protocol::TutorialMenuMsg;

fn log(msg: &str) {
    web_sys::console::log_1(&JsValue::from_str(msg));
}

fn set_status(msg: &str, css_class: &str) {
    let window = web_sys::window().unwrap();
    let document = window.document().unwrap();
    if let Some(el) = document.get_element_by_id("status") {
        el.set_text_content(Some(msg));
        el.set_class_name(css_class);
    }
}

/// Convert mouse event CSS coordinates to canvas-space coordinates.
/// When the canvas has CSS width/height different from its buffer size,
/// getBoundingClientRect returns the visual (CSS) size, so we must scale.
fn canvas_coords(e: &MouseEvent, canvas: &HtmlCanvasElement) -> (f64, f64) {
    let rect = canvas.get_bounding_client_rect();
    let css_x = e.client_x() as f64 - rect.left();
    let css_y = e.client_y() as f64 - rect.top();
    let rw = rect.width();
    let rh = rect.height();
    if rw == 0.0 || rh == 0.0 {
        return (css_x, css_y);
    }
    let scale_x = canvas.width() as f64 / rw;
    let scale_y = canvas.height() as f64 / rh;
    (css_x * scale_x, css_y * scale_y)
}

/// Scale the canvas CSS size to fit within the browser viewport while
/// preserving aspect ratio. Called every render frame (cheap when values
/// don't change) and on window resize.
fn fit_canvas_to_viewport(canvas: &HtmlCanvasElement) {
    let cw = canvas.width() as f64;
    let ch = canvas.height() as f64;
    if cw == 0.0 || ch == 0.0 {
        return;
    }

    let window = web_sys::window().unwrap();
    let vw = window.inner_width().unwrap().as_f64().unwrap_or(cw);
    let vh = window.inner_height().unwrap().as_f64().unwrap_or(ch);

    let scale = (vw / cw).min(vh / ch) * 0.93;
    let display_w = (cw * scale).floor();
    let display_h = (ch * scale).floor();

    let style = canvas.style();
    style.set_property("width", &format!("{}px", display_w)).ok();
    style.set_property("height", &format!("{}px", display_h)).ok();
}

#[wasm_bindgen(start)]
pub fn start() -> Result<(), JsValue> {
    let window = web_sys::window().unwrap();
    let document = window.document().unwrap();
    let canvas = document
        .get_element_by_id("empire")
        .unwrap()
        .dyn_into::<HtmlCanvasElement>()?;

    let state = Rc::new(RefCell::new(GameState::new()));
    let renderer = Rc::new(RefCell::new(Renderer::new(&canvas)?));

    // Connect WebSocket
    let location = window.location();
    let host = location.host()?;
    let protocol = location.protocol()?;
    let ws_protocol = if protocol == "https:" { "wss:" } else { "ws:" };
    let ws_url = format!("{}//{}/ws", ws_protocol, host);
    let ws = WebSocket::new(&ws_url)?;

    set_status("Connecting...", "connecting");

    // On open
    {
        let on_open = Closure::<dyn FnMut()>::new(move || {
            set_status("Connected", "connected");
        });
        ws.set_onopen(Some(on_open.as_ref().unchecked_ref()));
        on_open.forget();
    }

    // On message
    {
        let state_clone = state.clone();
        let on_message = Closure::<dyn FnMut(MessageEvent)>::new(move |e: MessageEvent| {
            if let Some(text) = e.data().as_string() {
                match serde_json::from_str::<protocol::ServerMessage>(&text) {
                    Ok(msg) => {
                        state_clone.borrow_mut().apply_message(msg);
                    }
                    Err(err) => {
                        log(&format!("Parse error: {}", err));
                    }
                }
            }
        });
        ws.set_onmessage(Some(on_message.as_ref().unchecked_ref()));
        on_message.forget();
    }

    // On close
    {
        let on_close = Closure::<dyn FnMut()>::new(move || {
            set_status("Disconnected", "disconnected");
        });
        ws.set_onclose(Some(on_close.as_ref().unchecked_ref()));
        on_close.forget();
    }

    // Keyboard input
    {
        let ws_clone = ws.clone();
        let state_clone = state.clone();
        let keydown = Closure::<dyn FnMut(KeyboardEvent)>::new(move |e: KeyboardEvent| {
            e.prevent_default();

            // Local-only: toggle help/controls overlay (do not send to server)
            // Support both 'H' and 'h' since the panel itself is what players want to hide.
            if e.key() == "H" || e.key() == "h" {
                let mut st = state_clone.borrow_mut();
                st.show_help_overlay = !st.show_help_overlay;
                return;
            }

            // Use current hover cell as the "mouse" coordinate for key commands.
            // This makes commands like '.', '*', 'u', etc work in the browser client.
            let (mx, my) = {
                let st = state_clone.borrow();
                (
                    st.hover_col.unwrap_or(0) as i32,
                    st.hover_row.unwrap_or(0) as i32,
                )
            };

            if let Some((mapped_key, msg)) = map_key_event(&e, mx, my) {
                {
                    let mut st = state_clone.borrow_mut();
                    match mapped_key.as_str() {
                        "P" => st.used_pause = true,
                        "?" => st.used_tutorial_menu = true,
                        "." => st.used_destination = true,
                        "*" => st.used_waypoint = true,
                        "!" => st.used_save = true,
                        "^" => st.used_load_menu = true,
                        "+" => st.used_map_cycle = true,
                        _ => {}
                    }
                }

                let _ = ws_clone.send_with_str(&msg);
            }
        });
        canvas.add_event_listener_with_callback("keydown", keydown.as_ref().unchecked_ref())?;
        keydown.forget();
    }
    {
        let ws_clone = ws.clone();
        let keyup = Closure::<dyn FnMut(KeyboardEvent)>::new(move |_e: KeyboardEvent| {
            let msg = r#"{"type":"key_up"}"#;
            let _ = ws_clone.send_with_str(msg);
        });
        canvas.add_event_listener_with_callback("keyup", keyup.as_ref().unchecked_ref())?;
        keyup.forget();
    }

    // Mouse click - sends col/row computed from canvas-space coordinates
    {
        let ws_clone = ws.clone();
        let canvas_clone = canvas.clone();
        let state_clone = state.clone();
        let mousedown = Closure::<dyn FnMut(MouseEvent)>::new(move |e: MouseEvent| {
            let (x, y) = canvas_coords(&e, &canvas_clone);

            // Check tutorial menu click first
            {
                let st = state_clone.borrow();
                if let Some(ref menu) = st.tutorial_menu {
                    let screen_w = canvas_clone.width() as f64;
                    let screen_h = canvas_clone.height() as f64;
                    if let Some(idx) = tutorial_menu_hit(menu, x, y, screen_w, screen_h) {
                        let id = &menu.scenarios[idx].id;
                        state_clone.borrow_mut().used_tutorial_menu = true;
                        let msg = format!(
                            r#"{{"type":"tutorial_select","id":"{}"}}"#,
                            id
                        );
                        let _ = ws_clone.send_with_str(&msg);
                        return;
                    }
                }
            }

            let col = (x / CELL_W) as i32;
            let row = (y / CELL_H) as i32;

            // Immediate local selection feedback
            {
                let mut st = state_clone.borrow_mut();
                if col >= 0 && row >= 0 {
                    st.selected_col = Some(col as usize);
                    st.selected_row = Some(row as usize);
                }
            }

            let button = match e.button() {
                0 => "left",
                2 => "right",
                _ => "left",
            };
            let msg = format!(
                r#"{{"type":"click","col":{},"row":{},"button":"{}"}}"#,
                col, row, button
            );
            let _ = ws_clone.send_with_str(&msg);
        });
        canvas.add_event_listener_with_callback("mousedown", mousedown.as_ref().unchecked_ref())?;
        mousedown.forget();
    }

    // Mouse move (hover) - throttled, sends col/row
    {
        let ws_clone = ws.clone();
        let canvas_clone = canvas.clone();
        let state_clone = state.clone();
        let last_hover = Rc::new(RefCell::new(0.0f64));
        let mousemove = Closure::<dyn FnMut(MouseEvent)>::new(move |e: MouseEvent| {
            let (x, y) = canvas_coords(&e, &canvas_clone);

            // Compute col/row from canvas-space coordinates
            let col = (x / CELL_W) as isize;
            let row = (y / CELL_H) as isize;

            // Update client-side hover tracking (always, for hover highlight)
            {
                let mut st = state_clone.borrow_mut();
                if col >= 0 && row >= 0 {
                    st.hover_col = Some(col as usize);
                    st.hover_row = Some(row as usize);
                } else {
                    st.hover_col = None;
                    st.hover_row = None;
                }

                // Track tutorial menu hover
                if let Some(ref menu) = st.tutorial_menu {
                    let screen_w = canvas_clone.width() as f64;
                    let screen_h = canvas_clone.height() as f64;
                    st.tutorial_menu_hovered = tutorial_menu_hit(menu, x, y, screen_w, screen_h);
                } else {
                    st.tutorial_menu_hovered = None;
                }
            }

            // Throttle WebSocket messages to 10/sec
            let now = js_sys::Date::now();
            let mut last = last_hover.borrow_mut();
            if now - *last < 100.0 { return; }
            *last = now;

            let msg = format!(r#"{{"type":"hover","col":{},"row":{}}}"#, col, row);
            let _ = ws_clone.send_with_str(&msg);
        });
        canvas.add_event_listener_with_callback("mousemove", mousemove.as_ref().unchecked_ref())?;
        mousemove.forget();
    }

    // Prevent context menu on right-click
    {
        let contextmenu = Closure::<dyn FnMut(MouseEvent)>::new(move |e: MouseEvent| {
            e.prevent_default();
        });
        canvas.add_event_listener_with_callback("contextmenu", contextmenu.as_ref().unchecked_ref())?;
        contextmenu.forget();
    }

    // Window resize handler to refit canvas
    {
        let canvas_clone = canvas.clone();
        let on_resize = Closure::<dyn FnMut()>::new(move || {
            fit_canvas_to_viewport(&canvas_clone);
        });
        window.add_event_listener_with_callback("resize", on_resize.as_ref().unchecked_ref())?;
        on_resize.forget();
    }

    // Focus canvas
    let _ = canvas.focus();

    // Start render loop
    start_render_loop(state, renderer, canvas);

    Ok(())
}

fn start_render_loop(
    state: Rc<RefCell<GameState>>,
    renderer: Rc<RefCell<Renderer>>,
    canvas: HtmlCanvasElement,
) {
    let f: Rc<RefCell<Option<Closure<dyn FnMut()>>>> = Rc::new(RefCell::new(None));
    let g = f.clone();

    let window = web_sys::window().unwrap();
    let perf = window.performance().unwrap();

    *g.borrow_mut() = Some(Closure::new(move || {
        let now = perf.now();
        let st = state.borrow();
        if st.map_size.0 > 0 {
            let mut r = renderer.borrow_mut();
            r.render(&st, now, &canvas);
            // Fit canvas to viewport after render sets canvas dimensions
            fit_canvas_to_viewport(&canvas);
        }
        let window = web_sys::window().unwrap();
        let _ = window.request_animation_frame(
            f.borrow().as_ref().unwrap().as_ref().unchecked_ref(),
        );
    }));

    let _ = window.request_animation_frame(
        g.borrow().as_ref().unwrap().as_ref().unchecked_ref(),
    );
}

/// Compute which tutorial menu item (index) is at canvas position (x, y),
/// given the screen dimensions. Returns None if not over any item.
fn tutorial_menu_hit(menu: &TutorialMenuMsg, x: f64, y: f64, screen_w: f64, screen_h: f64) -> Option<usize> {
    let padding = 24.0;
    let item_height = 48.0;
    let title_height = 40.0;
    let hint_height = 30.0;
    let menu_w = 520.0f64.min(screen_w * 0.85);
    let scenario_count = menu.scenarios.len();
    let menu_h = (title_height + padding + scenario_count as f64 * item_height + hint_height + padding)
        .min(screen_h * 0.85);
    let left = (screen_w - menu_w) / 2.0;
    let top = (screen_h - menu_h) / 2.0;
    let content_top = top + padding + title_height;

    if x < left || x > left + menu_w || y < content_top {
        return None;
    }

    let rel_y = y - content_top;
    let idx = (rel_y / item_height) as usize;
    if idx < scenario_count && rel_y >= 0.0 {
        Some(idx)
    } else {
        None
    }
}

fn map_key_event(e: &KeyboardEvent, mouse_x: i32, mouse_y: i32) -> Option<(String, String)> {
    let key = e.key();
    let shift = e.shift_key();

    let mapped = match key.as_str() {
        // Movement keys
        "q" | "w" | "e" | "a" | "d" | "z" | "x" | "c" => {
            if shift { key.to_uppercase() } else { key.clone() }
        }
        "Q" | "W" | "E" | "A" | "D" | "Z" | "X" | "C" => key.clone(),
        // Production keys
        "f" | "t" | "p" | "s" | "b" => {
            if shift { key.to_uppercase() } else { key.clone() }
        }
        "F" | "T" | "P" | "S" | "B" => key.clone(),
        // Special keys
        " " => "space".to_string(),
        "Escape" => "escape".to_string(),
        "`" => "`".to_string(),
        "!" => "!".to_string(),
        "." => ".".to_string(),
        "*" => "*".to_string(),
        "+" => "+".to_string(),
        "^" => "^".to_string(),
        // Unit command keys
        "u" => if shift { "U".to_string() } else { "u".to_string() },
        "U" => "U".to_string(),
        "l" => if shift { "L".to_string() } else { "l".to_string() },
        "L" => "L".to_string(),
        "m" => if shift { "M".to_string() } else { "m".to_string() },
        "M" => "M".to_string(),
        "o" => if shift { "O".to_string() } else { "o".to_string() },
        "O" => "O".to_string(),
        // Tutorial keys
        "n" => if shift { "N".to_string() } else { "n".to_string() },
        "N" => "N".to_string(),
        "?" => "?".to_string(),

        // Tips toggle (server-side)
        "h" => "h".to_string(),
        // Tips toggle (server-side) alternate binding for the browser client.
        // Lowercase 'h' is reserved for hiding the local help panel.
        "i" => "h".to_string(),
        _ => return None,
    };

    let msg = format!(
        r#"{{"type":"key","key":"{}","shift":{},"mouse_x":{},"mouse_y":{}}}"#,
        mapped, shift, mouse_x, mouse_y
    );

    Some((mapped, msg))
}
