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

pub fn x(i: i32) -> i32 {
    i + 1
}

mod tests {
    #[test]
    fn x_works() {
        use super::x;
        assert_eq!(x(1), 2)
    }
}
