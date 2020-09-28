use crate::{Genotype, ModeId, Nucleotide, Project, Schedule, Task, TaskId, TimeStamp};
use rand::seq::IteratorRandom;
use rand::Rng;

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

/// ## Example
///
/// - Resource has availability 3
/// - Task0 requires 2 units for 2 ticks.
/// - Task1 requires 1 unit for 4 ticks.
/// - Task2 requires 2 units for 1 tick.
/// - Task2 requires 2 units for 1 tick.
/// - Task0 is released for execution first.
/// - Task1 is released for execution second.
/// - All tasks are independent of each other.
///
/// ### Tick 1
///
/// - Execute both units of `Task0`
/// - Execute the unit of `Task1`
///
/// ### Tick 2
///
/// - Execute both units of `Task0`
/// - Execute two units of `Task1`
///
/// ## Algorithm
///
/// We're going to greedily 'pour' tasks into lanes.
/// To allocate a new task:
///
/// - Find the earliest time which has
pub struct ResourceQueue {

}

fn select_greedy_release_times(
    project: &Project,
    order: &[TaskId],
    modes: &[ModeId],
) -> Vec<TimeStamp> {
    let mut release_times = vec![0; order.len()];
    for task_id in order {
        // This handles tasks being scheduled in dependency order
        let task = &project.tasks[*task_id];

        // Get latest finish time of dependencies
        let dep_finish = match task
            .deps
            .iter()
            .map(|dep_id| {
                let mode_id = modes[*dep_id];
                let release = release_times[*dep_id];
                let duration = project.tasks[*dep_id].modes[mode_id].duration;
                release + duration
            })
            .max()
        {
            // Task has deps
            Some(end_time) => end_time,
            // No deps, can start immediately
            None => 0,
        };

        // FIXME: resource contraints
        release_times[*task_id] = dep_finish;
    }
    release_times
}

impl Genotype {
    /// Initialise a genotype with randomly-selected modes and greedily-assigned
    /// release times.
    pub fn new_greedy<R: rand::Rng>(project: &Project, mut rng: R) -> Self {
        // Build constituent parts of genotype
        let task_order = dep_order(&project, &mut rng);
        let modes = select_random_modes(&project.tasks, &mut rng);
        let release_times =
            select_greedy_release_times(project, task_order.as_slice(), modes.as_slice());

        // Zip them up together into a Genotype
        // Wow! Quite verbose...
        let mut nucs: Vec<Nucleotide> = izip!(&task_order, &modes, &release_times)
            .map(|(task, mode, release_time)| Nucleotide {
                task: *task,
                mode: *mode,
                release_time: *release_time as u32,
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{Mode, ModeRequirement, Project, Resource, Schedule};

    #[test]
    fn two_independent_tasks() {
        let prj = &Project {
            resources: vec![
                Resource {
                    name: String::from("Foo"),
                    cost: 1,
                    quantity: 1,
                },
                Resource {
                    name: String::from("Bar"),
                    cost: 1,
                    quantity: 1,
                },
            ],
            tasks: vec![
                Task {
                    deps: vec![],
                    modes: vec![Mode {
                        req: vec![ModeRequirement { quantity: 1, id: 0 }],
                        duration: 1,
                    }],
                },
                Task {
                    deps: vec![],
                    modes: vec![Mode {
                        req: vec![ModeRequirement { quantity: 1, id: 1 }],
                        duration: 1,
                    }],
                },
            ],
        };
        let order = &vec![0, 1];
        let modes = &vec![0, 0];
        assert_eq!(vec![0, 0], select_greedy_release_times(prj, order, modes))
    }

    #[test]
    fn two_serial_tasks() {
        let modes = vec![Mode {
            req: vec![ModeRequirement { quantity: 1, id: 0 }],
            duration: 1,
        }];
        let prj = &Project {
            resources: vec![Resource {
                name: String::from("Foo"),
                cost: 1,
                quantity: 1,
            }],
            tasks: vec![
                Task {
                    deps: vec![],
                    modes: modes.clone(),
                },
                Task {
                    deps: vec![0],
                    modes: modes,
                },
            ],
        };
        let order = &vec![0, 1];
        let modes = &vec![0, 0];
        assert_eq!(vec![0, 1], select_greedy_release_times(prj, order, modes))
    }

    #[test]
    fn two_independent_tasks_resource_conflict() {
        let modes = vec![Mode {
            req: vec![ModeRequirement { quantity: 1, id: 0 }],
            duration: 2,
        }];
        let prj = &Project {
            resources: vec![Resource {
                name: String::from("Foo"),
                cost: 1,
                quantity: 1,
            }],
            tasks: vec![
                Task {
                    deps: vec![],
                    modes: modes.clone(),
                },
                Task {
                    deps: vec![],
                    modes: modes,
                },
            ],
        };
        let order = &vec![0, 1];
        let modes = &vec![0, 0];
        assert_eq!(vec![0, 2], select_greedy_release_times(prj, order, modes))
    }

    static PRJ: &str = include_str!("sample.json");

    #[test]
    fn can_construct_greedy() {
        let prj: Project = serde_json::from_str(PRJ).unwrap();
        let schedule1 = Schedule::new_greedy(prj.clone());
        let schedule2 = Schedule::new_greedy(prj);
        // Sanity check: schedule0 and schedule2 have different genotypes.
        // assert_ne!(schedule1.genotype, schedule2.genotype, "Sanity check");
    }
}
