//! # Facilities for reading, writing, and manipulating projects
//!
//! Current problem: topological sort of project tasks
//!
//! ```diagram
//! a -> b -.
//!         |
//! c ------+-> d
//! ```
//!
//! partition-based topological sorts:  
//! -  `acbd`
//! - `cabd`
//!
//! what algorithm leads to this (valid) sort order?:  
//! - `abcd`

extern crate rand;
extern crate serde;
#[macro_use]
extern crate serde_derive;
extern crate serde_json;
extern crate uuid;
#[macro_use]
extern crate itertools;

use std::collections::HashMap;
use std::collections::HashSet;
use std::fs::File;
use std::io;
use std::path;

use rand::prelude::*;
use rand::seq::IteratorRandom;
use uuid::Uuid;

#[derive(Serialize, Deserialize, Debug)]
pub struct Resource {
    pub cost: i32,
    pub name: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Mode {
    #[serde(rename = "mode/duration")]
    pub duration: i32,
    #[serde(rename = "mode/req")]
    pub req: HashSet<(i32, Uuid)>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct Task {
    #[serde(rename = "deps/ids")]
    pub deps: HashSet<Uuid>,
    #[serde(rename = "modes/ids")]
    pub modes: HashSet<Uuid>,
}

/// The bare minumum of information we need to operate on a project.
/// Enriched with extra stuffs later.
#[derive(Serialize, Deserialize, Debug)]
pub struct Project {
    #[serde(rename = "r/modes")]
    pub modes: HashMap<Uuid, Mode>,

    #[serde(rename = "r/resources")]
    pub resources: HashMap<Uuid, Resource>,

    #[serde(rename = "r/tasks")]
    pub tasks: HashMap<Uuid, Task>,
}

/// A Nucleotide associates a task with a specific execution mode and release time.
#[derive(Serialize, Deserialize, Debug)]
pub struct Nucleotide {
    pub task: Uuid,
    pub mode: Uuid,
    pub release_time: i32,
}

/// A Genotype is a collection of [`Nucleotide`][nucleotide]s which fully describes
/// the execution time and mode of each task in the [Project][project].
///
/// [nucleotide]: struct.Nucleotide.html
/// [project]: struct.Project.html
#[derive(Debug, Serialize, Deserialize)]
pub struct Genotype(Vec<Nucleotide>);

impl Genotype {
    pub fn push(&mut self, n: Nucleotide) {
        self.0.push(n);
    }
}

/// The main artifact.
/// Contains a project with essential info about tasks, execution modes, and resources,
/// as well as a genotype which represents an assignment of tasks to modes and release times.
#[derive(Debug, Serialize, Deserialize)]
pub struct Schedule {
    pub project: Project,
    pub genotype: Genotype,
}

pub fn read_project(path: &path::PathBuf) -> io::Result<Project> {
    let f = File::open(path)?;
    let schedule = serde_json::from_reader(&f)?;

    Ok(schedule)
}

pub fn dep_order(project: &Project, rng: &mut ThreadRng) -> Vec<Uuid> {
    let mut no_deps: HashSet<Uuid> = HashSet::new();
    let mut order: Vec<Uuid> = Vec::with_capacity(project.tasks.len());

    while order.len() != project.tasks.len() {
        for tid in &project
            .tasks
            .iter()
            .filter(|(tid, _)| !no_deps.contains(tid))
            .filter(|(tid, _)| !order.contains(tid))
            .filter_map(|(tid, tval)| {
                for d in &tval.deps {
                    if !order.contains(&d) {
                        return None;
                    }
                }
                println!("eligible: {}", tid);
                Some(tid)
            })
            .collect::<Vec<&Uuid>>()
        {
            no_deps.insert(**tid);
        }

        if let Some(choice) = no_deps.iter().choose(rng) {
            println!("moving to order: {}", choice);
            order.push(*choice);
        }
        no_deps.remove(order.last().unwrap());
    }

    order
}

fn select_random_modes(project: &Project, order: &[Uuid], rng: &mut ThreadRng) -> Vec<Uuid> {
    order
        .iter()
        .map(|task_id| {
            project
                .tasks
                .get(task_id)
                .unwrap()
                .modes
                .iter()
                .choose(rng)
                .unwrap()
                .clone()
        })
        .collect()
}

fn select_greedy_release_times(project: &Project, modes: &[Uuid]) -> Vec<i32> {
    let mut resource_free_times = HashMap::new();
    let mut release_times = Vec::with_capacity(modes.len());
    for mode_id in modes {
        let mode = project.modes.get(mode_id).unwrap();
        let mut max = 0;
        for (_, resource_id) in &mode.req {
            let f = resource_free_times.entry(resource_id).or_insert(0);
            max = i32::max(max, *f);
            *f += mode.duration;
        }
        release_times.push(max);
    }
    release_times
}

impl Genotype {
    /// Initialise a genotype with randomly-selected modes and greedily-assigned
    /// release times.
    pub fn new_greedy(project: &Project) -> Self {
        let mut rng = rand::thread_rng();

        // Build constituent parts of genotype
        let order = dep_order(&project, &mut rng);
        let modes = select_random_modes(&project, &order, &mut rng);
        let release_times = select_greedy_release_times(&project, &modes);

        // Zip them up together into a Genotype
        // Wow! Quite verbose...
        Genotype(
            izip!(&order, &modes, &release_times)
                .map(|(task, mode, release_time)| Nucleotide {
                    task: *task,
                    mode: *mode,
                    release_time: *release_time,
                })
                .collect(),
        )
    }
}

impl Schedule {
    pub fn new_greedy(project: Project) -> Self {
        let genotype = Genotype::new_greedy(&project);
        Schedule { genotype, project }
    }
}

#[test]
fn can_call_greedy_project() {
    let s = r#"
{
    "r/modes": {
        "26b6b7b7-ba1a-4424-bef9-9daf5753c1ef": {
            "mode/duration": 120,
            "mode/req": [
                [
                    20,
                    "6834e02b-45f0-4efd-b9c9-144f296033d5"
                ]
            ]
        }
    },
    "r/resources": {
        "6834e02b-45f0-4efd-b9c9-144f296033d5": [
            10,
            "pastry chef"
        ]
    },
    "r/tasks": {
        "02dffcfc-3899-406c-afec-7cfb582bac98": {
            "deps/ids": [],
            "modes/ids": [
                "26b6b7b7-ba1a-4424-bef9-9daf5753c1ef"
            ]
        },
        "b5d0c1e7-e850-4e90-8794-c31b486139fb": {
            "deps/ids": [
                "02dffcfc-3899-406c-afec-7cfb582bac98"
            ],
            "modes/ids": [
                "26b6b7b7-ba1a-4424-bef9-9daf5753c1ef"
            ]
        },
        "6e710d54-9071-4dae-b942-e233b72926ba": {
            "deps/ids": [
                "02dffcfc-3899-406c-afec-7cfb582bac98",
                "c7f9a915-53e4-4f9c-ab69-dc497d2fe144"
            ],
            "modes/ids": [
                "26b6b7b7-ba1a-4424-bef9-9daf5753c1ef"
            ]
        },
        "c7f9a915-53e4-4f9c-ab69-dc497d2fe144": {
            "deps/ids": [],
            "modes/ids": [
                "26b6b7b7-ba1a-4424-bef9-9daf5753c1ef"
            ]
        }
    }
}
    "#;

    let project = serde_json::from_str(s).unwrap();
    let schedule = Schedule::new_greedy(project);
    println!(
        "Schedule: {}",
        serde_json::to_string_pretty(&schedule).unwrap()
    );
    assert_eq!(1, 1);
    let actual: Vec<_> = schedule
        .genotype
        .0
        .iter()
        .map(|nucleotide| nucleotide.release_time)
        .collect();
    assert_eq!(vec![0, 120, 240, 360], actual)
}