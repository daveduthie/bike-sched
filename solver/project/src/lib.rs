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

use std::fs::File;
use std::io;
use std::path;

pub mod crossover;
pub mod fitness;
pub mod greedy;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Resource {
    /// Not required. Kill?
    #[serde(rename = "resource/name")]
    name: String,
    /// Cost of using 1 unit of this resource for 1 unit of time
    #[serde(rename = "resource/cost")]
    cost: u64,
    /// How many units of this resource are available per unit of time
    #[serde(rename = "resource/quantity")]
    quantity: u64,
}

/// Ids are indices into arrays, so they're represented as `usize`s.
pub type ModeId = usize;
/// Ids are indices into arrays, so they're represented as `usize`s.
pub type TaskId = usize;
/// Ids are indices into arrays, so they're represented as `usize`s.
pub type ResourceId = usize;

#[derive(Serialize, Deserialize, Debug, Clone, Hash, Eq, PartialEq, Ord, PartialOrd)]
pub struct ModeRequirement {
    #[serde(rename = "req/id")]
    id: ModeId,
    #[serde(rename = "req/quant")]
    quantity: u64,
}

/// A Mode represents a manner in which a task can be executed. It
/// contains resource requirements as a list of `(quantity, id)`
/// tuples, and a `duration`--how long these resources would be
/// occupied for.
#[derive(Serialize, Deserialize, Debug, Clone, Hash, Eq, PartialEq, PartialOrd, Ord)]
pub struct Mode {
    #[serde(rename = "mode/duration")]
    pub duration: u64,
    #[serde(rename = "mode/requirements")]
    pub req: Vec<ModeRequirement>,
}

/// A unit of work
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Task {
    /// Set of tasks which must be completed before this task
    #[serde(rename = "task/deps")]
    pub deps: Vec<usize>,
    /// Set of modes in which this task can be completed
    #[serde(rename = "task/modes")]
    pub modes: Vec<Mode>,
}

/// The bare minumum of information we need to operate on a project.
/// Enriched with extra stuffs later.
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Project {
    #[serde(rename = "project/resources")]
    pub resources: Vec<Resource>,

    #[serde(rename = "project/tasks")]
    pub tasks: Vec<Task>,
}

impl Project {
    pub fn from_str(s: &str) -> Result<Self, serde_json::Error> {
        serde_json::from_str(s)
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, Copy)]
pub struct TimeStamp(f64);

type ReleaseTime = f64;

// impl PartialEq for TimeStamp {
//     fn eq(&self, o: &Self) -> bool {
//         let t: u64 = unsafe { mem::transmute(self.0) };
//         let t_o: u64 = unsafe { mem::transmute(o.0) };
//         t == t_o
//     }
// }

/// A Nucleotide associates a task with a specific execution mode and release time.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct Nucleotide {
    pub task: usize,
    pub mode: usize,
    pub release_time: f64,
}

/// A Genotype is a collection of [`Nucleotide`][nucleotide]s which fully describes
/// the execution time and mode of each task in the [Project][project].
///
/// [nucleotide]: struct.Nucleotide.html
/// [project]: struct.Project.html
#[derive(Debug, Deserialize, Serialize, Clone, PartialEq)]
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
    pub fn makespan(&self) -> f64 {
        let makespan = self
            .genotype
            .0
            .iter()
            .map(|n| {
                let duration = self
                    .project
                    .tasks
                    .get(n.task)
                    .unwrap()
                    .modes
                    .get(n.mode)
                    .unwrap()
                    .duration;
                n.release_time + duration as f64
            })
            .fold(0 as f64, |acc, nn| if acc < nn { nn } else { acc });

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
            vec![0.0, 0.0, 804.0, 308.0, 804.0, 1147.0, 148.0, 1257.0, 1363.0, 1257.0],
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
