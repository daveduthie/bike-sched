use rand::seq::IteratorRandom;

use crate::{Genotype, Mode, ModeId, Nucleotide, Project, Schedule, TaskId};

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

fn select_random_modes<R: rand::Rng>(project: &Project, rng: &mut R) -> Vec<ModeId> {
    project
        .tasks
        .iter()
        .map(|task| {
            let (mode_id, _mode): (ModeId, &Mode) =
                task.modes.iter().enumerate().choose(rng).unwrap();
            mode_id
        })
        .collect()
}

// Trying the following representation: `release_time[foo] = 0` means that task `foo` starts 0
// minutes after it's task dependencies have been met and the resources required by its mode
// become free.
fn select_greedy_release_times(project: &Project) -> Vec<u64> {
    let len = project.tasks.len();
    let mut release_times = Vec::with_capacity(len);
    for _ in 0..len {
        release_times.push(0)
    }
    release_times
}

impl Genotype {
    // Initialise a genotype with randomly-selected modes and greedily-assigned
    // release times.
    pub fn new_greedy<R: rand::Rng>(project: &Project, mut rng: R) -> Self {
        // Build constituent parts of genotype
        let order = dep_order(&project, &mut rng);
        let modes = select_random_modes(&project, &mut rng);

        let nucleotides = order
            .iter()
            .map(|task_id| Nucleotide {
                task: *task_id,
                mode: modes[*task_id],
                release_time: 0.0,
            })
            .collect();

        Genotype(nucleotides)
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
