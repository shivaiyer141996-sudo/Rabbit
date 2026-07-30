import type {
  AssessmentReport,
  AuditEvent,
  DashboardResponse,
  FacultyPerformance,
  IntelligenceOverview,
  NotificationInbox,
  QuestionPerformance,
  SettingsBundle,
} from "./types";

export const demoDashboard: DashboardResponse = {
  role: "ORG_ADMIN",
  greeting: "Welcome back, Ananya",
  description:
    "Academic performance, governance queues, and interventions in one view.",
  metrics: [
    {
      label: "Active students",
      value: "1,248",
      context: "Current organisation",
      tone: "PRIMARY",
      href: "/users",
    },
    {
      label: "Average score",
      value: "68.8%",
      context: "Published results",
      tone: "SUCCESS",
      href: "/reports",
    },
    {
      label: "Pass rate",
      value: "78.4%",
      context: "Configured grade threshold",
      tone: "INFO",
      href: "/reports",
    },
    {
      label: "At-risk students",
      value: "18",
      context: "Below threshold twice",
      tone: "DANGER",
      href: "/reports",
    },
  ],
  trend: [
    { label: "Apr", value: 57 },
    { label: "May", value: 61 },
    { label: "Jun", value: 64 },
    { label: "Jul", value: 69 },
  ],
  attention: [
    {
      title: "Questions waiting for review",
      description: "Complete the academic checklist before making a decision.",
      count: 6,
      severity: "WARNING",
      href: "/approvals",
    },
    {
      title: "Assessments waiting for approval",
      description: "Creator and reviewer separation is enforced.",
      count: 2,
      severity: "WARNING",
      href: "/approvals",
    },
    {
      title: "Evaluated results to publish",
      description: "Students cannot see scores until publication.",
      count: 32,
      severity: "INFO",
      href: "/reports",
    },
  ],
  unreadNotifications: 3,
};

export const demoOverview: IntelligenceOverview = {
  publishedResults: 428,
  averageScore: 68.8,
  passRate: 78.4,
  atRiskStudents: 18,
  completionRate: 91.2,
  scoreDistribution: [
    { label: "0–39", value: 42 },
    { label: "40–59", value: 96 },
    { label: "60–79", value: 174 },
    { label: "80–100", value: 116 },
  ],
  performanceTrend: demoDashboard.trend,
  recentAssessments: [
    {
      assessmentId: "77777777-7777-7777-7777-777777777703",
      title: "Physics Motion Progress Check",
      status: "COMPLETED",
      submissions: 124,
      averagePercentage: 72.4,
      passRate: 84.7,
    },
    {
      assessmentId: "77777777-7777-7777-7777-777777777702",
      title: "Physics Motion Diagnostic",
      status: "COMPLETED",
      submissions: 121,
      averagePercentage: 61.8,
      passRate: 69.4,
    },
  ],
};

export const demoQuestionAnalytics: QuestionPerformance[] = [
  {
    questionId: "55555555-5555-5555-5555-555555555501",
    code: "PHY-MEC-001",
    stem: "Displacement against time for constant acceleration",
    difficulty: "MEDIUM",
    usageCount: 8,
    responseCount: 244,
    correctRate: 72.5,
    difficultyIndex: 0.73,
    discriminationIndex: 0.42,
    poorQuality: false,
  },
  {
    questionId: "55555555-5555-5555-5555-555555555502",
    code: "PHY-MEC-002",
    stem: "Statements true for uniform velocity",
    difficulty: "MEDIUM",
    usageCount: 7,
    responseCount: 232,
    correctRate: 44.8,
    difficultyIndex: 0.45,
    discriminationIndex: 0.16,
    poorQuality: true,
  },
];

export const demoFaculty: FacultyPerformance[] = [
  {
    facultyUserId: "33333333-3333-3333-3333-333333333302",
    facultyName: "Sanjay Mehta",
    questionsAuthored: 86,
    approvedQuestions: 72,
    assessmentsCreated: 12,
    studentSubmissions: 386,
    averageStudentPercentage: 69.4,
  },
  {
    facultyUserId: "33333333-3333-3333-3333-333333333305",
    facultyName: "Varun Shah",
    questionsAuthored: 64,
    approvedQuestions: 57,
    assessmentsCreated: 9,
    studentSubmissions: 311,
    averageStudentPercentage: 72.1,
  },
];

export const demoAssessmentReport: AssessmentReport = {
  assessmentId: "77777777-7777-7777-7777-777777777703",
  title: "Physics Motion Progress Check",
  submissions: 124,
  averagePercentage: 72.4,
  highestPercentage: 100,
  lowestPercentage: 22.5,
  passRate: 84.7,
  scoreDistribution: demoOverview.scoreDistribution,
  studentResults: [
    {
      attemptId: "99999999-9999-9999-9999-999999999902",
      assessmentId: "77777777-7777-7777-7777-777777777703",
      assessmentTitle: "Physics Motion Progress Check",
      studentName: "Rohan Iyer",
      submittedAt: "2026-07-15T04:54:00Z",
      score: 8,
      maxScore: 8,
      percentage: 100,
      grade: "A",
      trajectory: "IMPROVING",
    },
  ],
  questionAnalytics: demoQuestionAnalytics,
  generatedAt: new Date().toISOString(),
  generatedBy: "admin@demo.rabbit.local",
};

export const demoNotifications: NotificationInbox = {
  unreadCount: 2,
  items: [
    {
      id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2",
      type: "WORKFLOW",
      title: "Question waiting for review",
      message: "CHE-ORG-014 is ready for academic review.",
      actionUrl: "/approvals",
      critical: true,
      deliveryStatus: "DELIVERED",
      read: false,
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    },
    {
      id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1",
      type: "RESULT_PUBLISHED",
      title: "Result publication completed",
      message: "Physics Motion Progress Check results are now visible.",
      actionUrl: "/reports",
      critical: false,
      deliveryStatus: "DELIVERED",
      read: false,
      createdAt: new Date(Date.now() - 26 * 60 * 60 * 1000).toISOString(),
    },
  ],
};

export const demoAuditEvents: AuditEvent[] = [
  {
    id: "cccccccc-cccc-cccc-cccc-ccccccccccc1",
    timestamp: "2026-07-15T06:30:00Z",
    actorUserId: "33333333-3333-3333-3333-333333333301",
    actorEmail: "admin@demo.rabbit.local",
    actorRole: "ORG_ADMIN",
    ipAddress: "127.0.0.1",
    module: "EVL",
    action: "PUBLISH_RESULTS",
    entityType: "Assessment",
    entityId: "77777777-7777-7777-7777-777777777703",
    status: "SUCCESS",
    beforeValue: "PENDING_PUBLICATION",
    afterValue: "PUBLISHED",
    traceId: "demo-trace-m2",
  },
  {
    id: "cccccccc-cccc-cccc-cccc-ccccccccccc2",
    timestamp: "2026-07-29T08:10:00Z",
    actorUserId: "33333333-3333-3333-3333-333333333303",
    actorEmail: "reviewer@demo.rabbit.local",
    actorRole: "REVIEWER",
    ipAddress: "127.0.0.1",
    module: "QRV",
    action: "APPROVE",
    entityType: "Question",
    status: "SUCCESS",
    beforeValue: "UNDER_REVIEW",
    afterValue: "APPROVED",
    traceId: "demo-review-trace",
  },
];

export const demoSettings: SettingsBundle = {
  general: {
    timezone: "Asia/Kolkata",
    language: "en",
    passPercentage: 40,
    atRiskThreshold: 40,
    defaultDurationMinutes: 45,
    defaultAttemptsAllowed: 1,
    shuffleQuestions: true,
    shuffleOptions: false,
    emailNotificationsEnabled: true,
    smsNotificationsEnabled: false,
    auditRetentionDays: 2555,
    displayName: "Rabbit Demo Academy",
    primaryColour: "#5936C8",
  },
  gradeBands: [
    { code: "A", label: "Excellent", minPercentage: 80, maxPercentage: 100 },
    { code: "B", label: "Very Good", minPercentage: 65, maxPercentage: 79.99 },
    { code: "C", label: "Good", minPercentage: 50, maxPercentage: 64.99 },
    { code: "D", label: "Developing", minPercentage: 40, maxPercentage: 49.99 },
    { code: "F", label: "Needs Support", minPercentage: 0, maxPercentage: 39.99 },
  ],
  subjects: [
    { id: "phy", code: "PHY", name: "Physics", active: true },
    { id: "che", code: "CHE", name: "Chemistry", active: true },
    { id: "mat", code: "MAT", name: "Mathematics", active: true },
  ],
  topics: [],
};
