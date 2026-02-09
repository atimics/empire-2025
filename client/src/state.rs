use crate::protocol::{ServerMessage, CellMsg, LoadMenuMsg, TutorialMsg, TutorialMenuMsg, TipsMsg};

pub struct GameState {
    pub map_size: (usize, usize), // (cols, rows)
    pub cells: Vec<Vec<Option<CellMsg>>>,
    pub round: u32,
    pub paused: bool,
    pub pause_requested: bool,
    pub waiting_for_input: bool,
    pub attention_coords: Vec<(usize, usize)>,
    pub attention_message: String,
    pub turn_message: String,
    pub error_message: String,
    pub error_until: f64,
    pub hover_message: String,
    pub production_status: String,
    pub destination: Option<(usize, usize)>,
    pub debug_message: String,
    pub load_menu: Option<LoadMenuMsg>,
    pub tutorial: Option<TutorialMsg>,
    pub tutorial_menu: Option<TutorialMenuMsg>,
    pub tips: Option<TipsMsg>,
    pub selected_col: Option<usize>,
    pub selected_row: Option<usize>,
    // Client-side hover tracking (not from server)
    pub hover_col: Option<usize>,
    pub hover_row: Option<usize>,
    // Client-side tutorial menu hover (not from server)
    pub tutorial_menu_hovered: Option<usize>,

    // --- Client-side onboarding / UI state ---
    pub show_help_overlay: bool,
    pub show_inspector: bool,
    pub used_pause: bool,
    pub used_tutorial_menu: bool,
    pub used_destination: bool,
    pub used_waypoint: bool,
    pub used_save: bool,
    pub used_load_menu: bool,
    pub used_map_cycle: bool,
}

impl GameState {
    pub fn new() -> Self {
        Self {
            map_size: (0, 0),
            cells: vec![],
            round: 0,
            paused: false,
            pause_requested: false,
            waiting_for_input: false,
            attention_coords: vec![],
            attention_message: String::new(),
            turn_message: String::new(),
            error_message: String::new(),
            error_until: 0.0,
            hover_message: String::new(),
            production_status: String::new(),
            destination: None,
            debug_message: String::new(),
            load_menu: None,
            tutorial: None,
            tutorial_menu: None,
            tips: None,
            selected_col: None,
            selected_row: None,
            hover_col: None,
            hover_row: None,
            tutorial_menu_hovered: None,

            show_help_overlay: true,
            show_inspector: true,
            used_pause: false,
            used_tutorial_menu: false,
            used_destination: false,
            used_waypoint: false,
            used_save: false,
            used_load_menu: false,
            used_map_cycle: false,
        }
    }

    pub fn apply_message(&mut self, msg: ServerMessage) {
        match msg {
            ServerMessage::State(s) => {
                self.map_size = s.map_size;
                self.cells = s.cells;
                self.round = s.round;
                self.paused = s.paused;
                self.pause_requested = s.pause_requested;
                self.waiting_for_input = s.waiting_for_input;
                self.attention_coords = s.attention_coords
                    .iter()
                    .filter_map(|c| {
                        if c.len() == 2 { Some((c[0], c[1])) } else { None }
                    })
                    .collect();
                self.attention_message = s.attention_message;
                self.turn_message = s.turn_message;
                self.error_message = s.error_message;
                self.error_until = s.error_until;
                self.hover_message = s.hover_message;
                (self.selected_col, self.selected_row) = s
                    .selected_cell
                    .as_ref()
                    .and_then(|c| if c.len() == 2 { Some((c[0], c[1])) } else { None })
                    .map(|(c, r)| (Some(c), Some(r)))
                    .unwrap_or((None, None));
                self.production_status = s.production_status;
                self.destination = s.destination.as_ref().and_then(|d| {
                    if d.len() == 2 { Some((d[0], d[1])) } else { None }
                });
                self.debug_message = s.debug_message;
                self.load_menu = s.load_menu;
                self.tutorial = s.tutorial;
                self.tutorial_menu = s.tutorial_menu;
                self.tips = s.tips;
            }
        }
    }
}
