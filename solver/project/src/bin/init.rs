//! Trying out a module comment

extern crate project;
extern crate serde_json;

use project::{Genotype, Project};
use std::io;

pub fn main() {
    let p: serde_json::Result<Project> = serde_json::from_reader(io::stdin());
    match p {
        Err(e) => println!("not cool: {}", e),
        Ok(project) => println!("Project: {:?}", Genotype::new_greedy(&project)),
    }
}
