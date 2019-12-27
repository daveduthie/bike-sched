use rand::seq::IteratorRandom;
use rand::Rng;

use crate::{Genotype, ModeId, Nucleotide, Project, Schedule, Task, TaskId, TimeStamp};

pub fn dep_order<R: rand::Rng>(project: &Project, rng: &mut R) -> Vec<TaskId> {
    let mut no_deps: Vec<TaskId> = Vec::new();
    let mut order: Vec<TaskId> = Vec::with_capacity(project.tasks.len());

    while order.len() != project.tasks.len() {
        let mut new_no_deps: Vec<_> = project
            .tasks
            .iter()
            .enumerate()
            .filter(|(tid, _)| !no_deps.contains(tid))
            .filter(|(tid, _)| !order.contains(tid))
            .filter_map(|(tid, tval)| {
                for d in &tval.deps {
                    if !order.contains(&d) {
                        return None;
                    }
                }
                Some(tid)
            })
            .collect();
        {
            no_deps.append(&mut new_no_deps);
            no_deps.sort();
            // println!("eligible: {:#?}", no_deps);
        }

        if let Some(choice) = no_deps.iter().choose(rng) {
            println!("moving to order: {}", choice);
            order.push(*choice);
        }
        let ol = order.last().unwrap();
        no_deps
            .iter()
            .position(|item| ol == item)
            .map(|i| no_deps.remove(i));
        // Equivalent but unstable one built in method
        // no_deps.remove_item(order.last().unwrap());
    }

    order
}

fn select_random_modes<R: Rng>(tasks: &[Task], rng: &mut R) -> Vec<ModeId> {
    tasks
        .iter()
        .map(|t| rng.gen_range(0, t.modes.len()))
        .collect()
}

fn select_greedy_release_times(_project: &Project, _modes: &[ModeId]) -> Vec<TimeStamp> {
    unimplemented!()
}

impl Genotype {
    /// Initialise a genotype with randomly-selected modes and greedily-assigned
    /// release times.
    pub fn new_greedy<R: rand::Rng>(project: &Project, mut rng: R) -> Self {
        // Build constituent parts of genotype
        let order = dep_order(&project, &mut rng);
        let modes = select_random_modes(&project.tasks, &mut rng);
        let release_times = select_greedy_release_times(project, modes.as_slice());

        // Zip them up together into a Genotype
        // Wow! Quite verbose...
        let mut nucs: Vec<Nucleotide> = izip!(&order, &modes, &release_times)
            .map(|(task, mode, release_time)| Nucleotide {
                task: *task,
                mode: *mode,
                release_time: *release_time,
            })
            .collect();
        // Want to ensure we sort genotype by task id so we don't have to keep doing it later.
        nucs.sort_by_key(|n| n.task);
        Genotype(nucs)
    }
}

impl Schedule {
    pub fn new_greedy_seeded<R: rand::Rng>(project: Project, rng: &mut R) -> Self {
        let genotype = Genotype::new_greedy(&project, rng);
        Schedule { genotype, project }
    }

    pub fn new_greedy(project: Project) -> Self {
        let mut rng = rand::thread_rng();
        Schedule::new_greedy_seeded(project, &mut rng)
    }
}
