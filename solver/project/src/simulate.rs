//! What's the idea here?
//!
//! I'd like to assign tasks to their earliest possible execution times. Inputs
//! should be a dependency order and a list of modes selected for each task.
//!
//! Problem:
//!
//! Schedule a task at the next available time, given it's resource requirements
//! such that it is not started ahead of any previously scheduled task and no
//! resource constraints are violated.
//!
//! Algorithm:
//!
//! - Keep a queue of done, in-progress, todo tasks per resource.
//! - Move tasks to in-progress when there is excess capacity.
//! - When there is no excess capacity, move the first in-progress task to done and update the current time.

#![allow(dead_code)]

use crate::{Duration, ResourceQuantity, TaskId, TimeStamp};

use std::collections::VecDeque;

/// The piece of a `Task` which is executed by the resource this appears within.
#[derive(Debug)]
struct TaskFragmentTodo {
    parent: TaskId,
    quantity: ResourceQuantity,
    duration: Duration,
}

impl TaskFragmentTodo {
    fn new(parent: TaskId, quantity: ResourceQuantity, duration: Duration) -> Self {
        TaskFragmentTodo {
            parent,
            quantity,
            duration,
        }
    }
}

#[derive(Debug)]
struct TaskFragmentStarted {
    parent: TaskId,
    quantity: ResourceQuantity,
    end_time: TimeStamp,
}

#[derive(Debug)]
struct TaskFragmentDone {
    parent: TaskId,
    end_time: TimeStamp,
}

#[derive(Debug)]
struct ResourceSim {
    current_time: TimeStamp,
    available_capacity: u64,

    todo: VecDeque<TaskFragmentTodo>,
    in_progress: VecDeque<TaskFragmentStarted>,
    done: VecDeque<TaskFragmentDone>,
}

impl ResourceSim {
    fn new(capacity: u64) -> Self {
        ResourceSim {
            current_time: 0.0,
            available_capacity: capacity,

            todo: VecDeque::new(),
            in_progress: VecDeque::new(),
            done: VecDeque::new(),
        }
    }
    /// Moar documentation
    fn next_time_for_capacity(&mut self, capacity: u64) -> TimeStamp {
        while let Some(TaskFragmentTodo {
            parent: parent,
            quantity: quantity,
            duration: duration,
        }) = self.todo.front()
        {
            if *quantity <= self.available_capacity {
                let work = self.todo.pop_front().unwrap(); // we already know there's an element
                self.available_capacity -= *quantity;
                self.in_progress.push_back(TaskFragmentStarted {
                    parent: *parent,
                    quantity: *quantity,
                    end_time: self.current_time + *duration as f64,
                });
            // TODO: sort by end time?
            } else {
                // should have something in progress
                let TaskFragmentStarted {
                    parent: parent,
                    quantity: quantity,
                    end_time: end_time,
                } = self.in_progress.pop_front().expect(
                    "Why is there nothing in progress? This probably means a task has \
                        required quantity > the resource's total capacity :/",
                );
                self.available_capacity += quantity;
                self.current_time = end_time;
            }
        }

        while self.available_capacity < capacity {
            let TaskFragmentStarted {
                parent: parent,
                quantity: quantity,
                end_time: end_time,
            } = self.in_progress.pop_front().expect("mmm");
            self.available_capacity += quantity;
            self.current_time = end_time;
            self.done.push_back(TaskFragmentDone { parent, end_time });
        }

        self.current_time
    }
}

#[test]
fn can_do_a_thing_with_resource_sim() {
    let mut rs = ResourceSim::new(2);
    rs.todo.append(&mut VecDeque::from(vec![
        TaskFragmentTodo::new(0, 1, 2),
        TaskFragmentTodo::new(1, 2, 1),
    ]));

    assert_eq!(3.0, rs.next_time_for_capacity(2));
}
