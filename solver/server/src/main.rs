#![feature(proc_macro_hygiene, decl_macro)]

#[macro_use]
extern crate rocket;
extern crate project;

use project::Project;
use rocket_contrib::json::Json;

#[get("/")]
fn index() -> &'static str {
    "Hello, world!"
}

#[post("/schedule", format = "application/json", data = "<project>")]
fn create_schedule(project: Json<Project>) -> Json<Project> {
    project
    // let Json(p) = project;
    // format!("The data: {:?}", p)
}


fn main() {
    rocket::ignite()
        .mount("/", routes![index, create_schedule])
        .launch();
}
