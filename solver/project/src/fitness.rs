use crate::Schedule;

/// Evaluate the fitness of a solution. Right now it just calculates
/// the makespan but in future, this could be extended penalise
/// infeasibility (e.g. resource use conflicts) or to take job
/// deadlines into account.
pub fn fitness(schedule: &Schedule) -> Vec<u32> {
    let makespan = schedule.makespan();
    // For now, return makespan as the only component of the fitness function
    vec![makespan]
}
