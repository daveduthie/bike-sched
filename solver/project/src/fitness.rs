use crate::{Genotype, Schedule};

/// Evaluate the fitness of a solution. Right now it just calculates
/// the makespan but in future, this could be extended penalise
/// infeasibility (e.g. resource use conflicts) or to take job
/// deadlines into account.
pub fn fitness(schedule: &Schedule) -> Vec<i64> {
    let Genotype(nucleotides) = &schedule.genotype;
    let mut nucs = nucleotides.clone();
    nucs.sort_by_key(|n| n.release_time);
    let nuc = nucs.last().unwrap();
    let duration = schedule
        .project
        .tasks
        .get(&nuc.task)
        .unwrap()
        .modes
        .get(&nuc.mode)
        .unwrap()
        .duration;
    let makespan = nuc.release_time + duration;
    // For now, return makespan as the only component of the fitness function
    vec![makespan]
}
