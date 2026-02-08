use serde::Deserialize;

#[derive(Deserialize, Debug)]
#[serde(tag = "type")]
pub enum ServerMessage {
    #[serde(rename = "state")]
    State(GameStateMsg),
}

#[derive(Deserialize, Debug)]
pub struct GameStateMsg {
    pub map_size: (usize, usize),
    pub cells: Vec<Vec<Option<CellMsg>>>,
    pub round: u32,
    pub paused: bool,
    pub pause_requested: bool,
    pub waiting_for_input: bool,
    pub attention_coords: Vec<Vec<usize>>,
    pub attention_message: String,
    pub turn_message: String,
    pub error_message: String,
    pub error_until: f64,
    pub hover_message: String,
    pub selected_cell: Option<Vec<usize>>,
    pub production_status: String,
    pub destination: Option<Vec<usize>>,
    pub map_to_display: String,
    pub debug_message: String,
    pub load_menu: Option<LoadMenuMsg>,
    pub tutorial: Option<TutorialMsg>,
    pub tutorial_menu: Option<TutorialMenuMsg>,
    pub tips: Option<TipsMsg>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TipsMsg {
    pub id: String,
    pub title: String,
    pub text: String,
}

#[derive(Deserialize, Debug, Clone)]
pub struct CellMsg {
    pub t: String,
    pub cs: Option<String>,
    pub u: Option<UnitMsg>,
    pub wp: Option<bool>,
    pub fc: Option<u8>,
    pub ac: Option<u8>,
    pub af: Option<u8>,
    pub aa: Option<u8>,
    pub cid: Option<u32>,
    pub prod: Option<ProductionMsg>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct UnitMsg {
    pub t: String,
    pub o: String,
    pub m: String,
    pub h: Option<u32>,
    pub fuel: Option<u32>,
    #[serde(rename = "marching-orders")]
    pub marching_orders: Option<serde_json::Value>,
    #[serde(rename = "flight-path")]
    pub flight_path: Option<serde_json::Value>,
    #[serde(rename = "transport-mission")]
    pub transport_mission: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct ProductionMsg {
    pub item: String,
    pub remaining: u32,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LoadMenuMsg {
    pub files: Vec<String>,
    pub hovered: Option<usize>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TutorialMsg {
    pub page_text: String,
    pub page_index: u32,
    pub page_count: u32,
    pub scenario_name: String,
    pub overlay_visible: bool,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TutorialMenuMsg {
    pub scenarios: Vec<TutorialScenarioMsg>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TutorialScenarioMsg {
    pub id: String,
    pub name: String,
    pub description: String,
}
