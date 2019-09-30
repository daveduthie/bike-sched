//! # Facilities for reading, writing, and manipulating projects

#![feature(test)]
#[macro_use]
extern crate itertools;
extern crate rand;
extern crate serde;
#[macro_use]
extern crate serde_derive;
extern crate serde_json;
extern crate test;
extern crate uuid;

use std::collections::HashMap;
use std::fs::File;
use std::io;
use std::path;

use uuid::Uuid;

pub mod crossover;
pub mod fitness;
pub mod greedy;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Resource(String, u64);

/// A Mode represents a manner in which a task can be executed. It
/// contains resource requirements as a list of `(quantity, id)`
/// tuples, and a `duration`--how long these resources would be
/// occupied for.
#[derive(Serialize, Deserialize, Debug, Clone, Hash, Eq, PartialEq, PartialOrd, Ord)]
pub struct Mode {
    #[serde(rename = "mode/duration")]
    pub duration: i64,
    #[serde(rename = "mode/requirements")]
    pub req: Vec<(i64, Uuid)>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Task {
    #[serde(rename = "task/deps")]
    pub deps: Vec<Uuid>,
    #[serde(rename = "task/modes")]
    pub modes: HashMap<Uuid, Mode>,
}

/// The bare minumum of information we need to operate on a project.
/// Enriched with extra stuffs later.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Project {
    #[serde(rename = "project/resources")]
    pub resources: HashMap<Uuid, Resource>,

    #[serde(rename = "project/tasks")]
    pub tasks: HashMap<Uuid, Task>,
}

impl Project {
    pub fn from_str(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }
}

/// A Nucleotide associates a task with a specific execution mode and release time.
#[derive(Serialize, Deserialize, Debug, Clone, Eq, PartialEq)]
pub struct Nucleotide {
    pub task: Uuid,
    pub mode: Uuid,
    pub release_time: i64,
}

/// A Genotype is a collection of [`Nucleotide`][nucleotide]s which fully describes
/// the execution time and mode of each task in the [Project][project].
///
/// [nucleotide]: struct.Nucleotide.html
/// [project]: struct.Project.html
#[derive(Debug, Deserialize, Serialize, Clone, Eq, PartialEq)]
pub struct Genotype(Vec<Nucleotide>);

impl Genotype {
    pub fn push(&mut self, n: Nucleotide) {
        self.0.push(n);
    }
}

/// The main artifact.
/// Contains a project with essential info about tasks, execution modes, and resources,
/// as well as a genotype which represents an assignment of tasks to modes and release times.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Schedule {
    pub project: Project,
    pub genotype: Genotype,
}

impl Schedule {
    pub fn makespan(&self) -> i64 {
        let makespan = self
            .genotype
            .0
            .iter()
            .map(|n| {
                let duration = self
                    .project
                    .tasks
                    .get(&n.task)
                    .unwrap()
                    .modes
                    .get(&n.mode)
                    .unwrap()
                    .duration;
                n.release_time + duration
            })
            .fold(0, |acc, nn| if acc < nn { nn } else { acc });

        makespan
    }
}

pub fn read_project(path: &path::PathBuf) -> io::Result<Project> {
    let f = File::open(path)?;
    let schedule = serde_json::from_reader(&f)?;

    Ok(schedule)
}

#[cfg(test)]
mod tests {
    use rand::rngs;
    use test::Bencher;

    use super::*;

    static PRJ: &str = include_str!("sample.json");

    fn test_rng() -> rngs::mock::StepRng {
        rand::rngs::mock::StepRng::new(1, 0)
    }

    #[test]
    fn can_call_greedy_project() {
        let project = serde_json::from_str(PRJ).unwrap();
        let schedule = Schedule::new_greedy_seeded(project, &mut test_rng());

        println!(
            "Genotype:\n {}",
            serde_json::to_string_pretty(&schedule.genotype).unwrap()
        );

        let actual: Vec<_> = schedule
            .genotype
            .0
            .iter()
            .map(|nucleotide| nucleotide.release_time)
            .collect();
        assert_eq!(
            vec![0, 0, 804, 308, 804, 1147, 148, 1257, 1363, 1257],
            actual
        )
    }

    #[test]
    fn can_call_fitness() {
        let project: Project = serde_json::from_str(PRJ).unwrap();
        let schedule = Schedule::new_greedy(project);
        let fitness = fitness::fitness(&schedule);
        assert_eq!(fitness.len(), 1)
    }

    #[bench]
    fn bench_new_greedy_schedule(b: &mut Bencher) {
        let project: Project = serde_json::from_str(PRJ).unwrap();
        b.iter(|| Schedule::new_greedy(project.clone()))
    }
}
