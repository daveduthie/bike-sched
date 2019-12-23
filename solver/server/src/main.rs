#![feature(proc_macro_hygiene, decl_macro)]

#[macro_use]
extern crate rocket;
extern crate project;

use project::{Project, Schedule};
use rocket::Request;
use rocket_contrib::json::Json;

#[get("/")]
fn index() -> &'static str {
    "Hello, world!"
}

#[post("/schedule", format = "application/json", data = "<project>")]
fn create_schedule(project: Json<Project>) -> Json<Schedule> {
    let s = Schedule::new_greedy(project.into_inner());
    Json(s)
}

#[catch(422)]
fn malformed_input(_req: &Request) -> String {
    "Nope".to_string()
}

fn main() {
    rocket::ignite()
        .register(catchers![malformed_input])
        .mount("/", routes![index, create_schedule])
        .launch();
}
