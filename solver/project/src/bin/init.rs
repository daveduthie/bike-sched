//! Trying out a module comment

extern crate project;
extern crate serde_json;
extern crate uuid;

use uuid::Uuid;

/// Read a project in from a magic path, add a new resource, and print it as
/// JSON. Functionality will be moved to an init module which will implement
/// strategies for creating an initial population of genotypes given a
/// [`Project`][project].
///
/// [project]: ../project/struct.Project.html
pub fn main() {
    // let mut p = std::env::current_dir().unwrap();
    // p.push("project/sample.json");
    // let mut schedule = project::read_project(&p).unwrap();

    // schedule.resources.insert(
    //     Uuid::new_v4(),
    //     project::Resource {
    //         cost: 13,
    //         name: "Rabbit".to_string(),
    //     },
    // );

    // let j = serde_json::to_string_pretty(&schedule).unwrap();
    // println! {"as JSON: {}", &j}

    // let mut rng = rand::thread_rng();
    // let order = project::dep_order(&schedule, &mut rng);
    // println!(
    //     "The order: {}",
    //     serde_json::to_string_pretty(&order).expect("no")
    // );
    // let project = project::pick_modes_and_release_times(&schedule, &order, &mut rng);
    // println!(
    //     "first project: {}",
    //     serde_json::to_string_pretty(&project).expect("no!")
    // );
}
