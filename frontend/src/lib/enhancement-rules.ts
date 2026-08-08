export function addUnique(values: string[], value: string) {
  return values.includes(value) ? values : [...values, value];
}

export function questionIdsForRemovedSubject(
  questions: Array<{ id: string; subjectId: string }>,
  selectedIds: string[],
  subjectId: string,
) {
  return questions
    .filter((question) => question.subjectId === subjectId && selectedIds.includes(question.id))
    .map((question) => question.id);
}

export function matchesAssessmentQuestionFilters(
  question: { code: string; stem: string; subjectId: string; topicId: string; difficulty: string; type: string },
  selectedSubjectIds: string[],
  filters: { query: string; subjectId: string; topicId: string; difficulty: string; type: string },
) {
  if (!selectedSubjectIds.includes(question.subjectId)) return false;
  if (filters.subjectId && question.subjectId !== filters.subjectId) return false;
  if (filters.topicId && question.topicId !== filters.topicId) return false;
  if (filters.difficulty && question.difficulty !== filters.difficulty) return false;
  if (filters.type && question.type !== filters.type) return false;
  return `${question.code} ${question.stem}`.toLowerCase().includes(filters.query.toLowerCase());
}

export function activeSectionOptions<T extends { status: string }>(sections: T[]) {
  return sections.filter((section) => section.status === "ACTIVE");
}

export function reviewSelectionState(selected: string[], mandatory: string[]) {
  const count = mandatory.filter((item) => selected.includes(item)).length;
  return {
    all: count === mandatory.length,
    none: count === 0,
    partial: count > 0 && count < mandatory.length,
    approveEnabled: count === mandatory.length,
  };
}

export function studentPortalRouteAllowed(pathname: string) {
  return ["/dashboard", "/notifications", "/profile"]
    .some((route) => pathname === route || pathname.startsWith(`${route}/`));
}
