/** Fake sample data (matches the UI mockup). Not real people or jobs. */
import type { JobDto, Level, StatusEventDto } from '@/api/types';

export interface MockSkill {
  slug: string;
  name: string;
  category: string;
}

export const SKILLS: MockSkill[] = [
  ['java', 'Java', 'LANGUAGE'],
  ['docker', 'Docker', 'DEVOPS'],
  ['kubernetes', 'Kubernetes', 'DEVOPS'],
  ['react', 'React', 'FRAMEWORK'],
  ['python', 'Python', 'LANGUAGE'],
  ['aws', 'AWS', 'CLOUD'],
  ['spring-boot', 'Spring Boot', 'FRAMEWORK'],
  ['postgresql', 'PostgreSQL', 'DATABASE'],
  ['typescript', 'TypeScript', 'LANGUAGE'],
  ['kafka', 'Kafka', 'DATABASE'],
  ['terraform', 'Terraform', 'DEVOPS'],
  ['go', 'Go', 'LANGUAGE'],
  ['sql', 'SQL', 'LANGUAGE'],
  ['grpc', 'gRPC', 'FRAMEWORK'],
  ['ruby', 'Ruby', 'LANGUAGE'],
  ['vue', 'Vue', 'FRAMEWORK'],
  ['css', 'CSS', 'LANGUAGE'],
  ['rest', 'REST APIs', 'PRACTICE'],
  ['cicd', 'CI/CD', 'DEVOPS'],
].map(([slug, name, category]) => ({ slug, name, category }) as MockSkill);

export const INITIAL_LEVELS: Record<string, Level> = {
  java: 2,
  docker: 1,
  kubernetes: 0,
  react: 3,
  python: 1,
  aws: 1,
  'spring-boot': 3,
  postgresql: 2,
  typescript: 2,
  kafka: 0,
  terraform: 0,
  go: 0,
};

type Seed = [
  id: string,
  company: string,
  title: string,
  status: JobDto['application']['status'],
  daysAgo: number,
  required: string[],
  preferred: string[],
  text: string,
  extra?: Partial<JobDto> & { nextActionAt?: string; notes?: string },
];

const SEEDS: Seed[] = [
  [
    'j-dbs',
    'DBS Bank',
    'Software Engineer II',
    'SAVED',
    3,
    ['java', 'spring-boot', 'KUBERNETES'],
    ['aws'],
    'Deploy Java services to Kubernetes clusters on AWS. Experience with Helm is a plus.',
  ],
  [
    'j-grab',
    'Grab',
    'Backend Engineer',
    'SAVED',
    4,
    ['go', 'postgresql', 'KAFKA'],
    ['kubernetes'],
    'You will tune PostgreSQL and Kafka at scale. Kubernetes knowledge is useful.',
  ],
  [
    'j-shopee',
    'Shopee',
    'Full-stack Engineer',
    'SAVED',
    5,
    [],
    [],
    'Build React and Node.js features. Our services ship as containers on Kubernetes.',
    { extractionStatus: 'PENDING' },
  ],
  [
    'j-govtech',
    'GovTech',
    'Full-stack Developer',
    'APPLIED',
    5,
    ['react', 'spring-boot', 'aws', 'java', 'rest', 'postgresql', 'docker'],
    ['kubernetes', 'terraform', 'DEVOPS'],
    'You will build citizen-facing services with a small product team. You will own features end to end, from the React front end to Spring Boot APIs on AWS.\n\nWhat you need: 2+ years with React and Java / Spring Boot. Good with REST APIs and PostgreSQL. Some AWS and Docker.\n\nGood to have: Kubernetes, Terraform, CI/CD pipelines.',
    {
      location: 'Singapore · Hybrid',
      sourceUrl: 'https://careers.gov.sg/job/123',
      nextActionAt: 'TODAY',
      notes:
        'Applied through careers.gov.sg. Team uses React + Spring Boot on AWS. Ask about their Kubernetes migration.',
    },
  ],
  [
    'j-ocbc',
    'OCBC',
    'Java Developer',
    'APPLIED',
    6,
    ['java', 'docker', 'SQL'],
    [],
    'Build Spring Boot APIs and package them with Docker for our private cloud.',
  ],
  [
    'j-sea',
    'Sea',
    'Software Engineer',
    'APPLIED',
    10,
    ['python', 'docker', 'AWS'],
    [],
    'Write Python services on AWS with Docker and strong test coverage.',
  ],
  [
    'j-ncs',
    'NCS',
    'Cloud Engineer',
    'INTERVIEW',
    6,
    ['aws', 'terraform', 'KUBERNETES'],
    [],
    'Design and run Kubernetes platforms for government agencies, using Terraform and GitOps.',
    { nextActionAt: 'TODAY' },
  ],
  [
    'j-ascenda',
    'Ascenda',
    'Software Engineer',
    'INTERVIEW',
    2,
    ['react', 'ruby', 'AWS'],
    [],
    'Ship React and Ruby features for our loyalty platform on AWS.',
  ],
  [
    'j-carousell',
    'Carousell',
    'Backend Engineer',
    'REJECTED',
    14,
    ['go', 'kubernetes', 'GRPC'],
    ['kafka'],
    'Our Go services run on Kubernetes with gRPC between them and Kafka for events.',
  ],
  [
    'j-pg',
    'PropertyGuru',
    'Frontend Engineer',
    'WITHDRAWN',
    20,
    ['vue', 'typescript', 'CSS'],
    [],
    'Build Vue and TypeScript interfaces with clean, accessible CSS.',
  ],
];

const nameOf = (slug: string) => SKILLS.find((s) => s.slug === slug)?.name ?? slug;

export function buildJobs(now: Date, levels: Record<string, Level>): JobDto[] {
  const today = now.toISOString().slice(0, 10);
  return SEEDS.map(([id, company, title, status, daysAgo, req, pref, text, extra = {}]) => {
    const { nextActionAt, notes, ...rest } = extra as Partial<JobDto> & {
      nextActionAt?: string;
      notes?: string;
    };
    const createdAt = new Date(now.getTime() - daysAgo * 86_400_000).toISOString();
    return {
      id,
      company,
      title,
      location: 'Singapore',
      sourceUrl: null,
      rawText: text,
      seniority: 'MID',
      minYears: 2,
      extractionStatus: 'DONE',
      skills: [
        ...req.map((slug) => ({
          slug,
          name: nameOf(slug),
          importance: 'REQUIRED' as const,
          myLevel: levels[slug] ?? 0,
        })),
        ...pref.map((slug) => ({
          slug,
          name: nameOf(slug),
          importance: 'PREFERRED' as const,
          myLevel: levels[slug] ?? 0,
        })),
      ],
      application: {
        id: `a-${id}`,
        status,
        appliedAt: status === 'SAVED' ? null : createdAt,
        nextActionAt: nextActionAt === 'TODAY' ? today : null,
        notes: notes ?? null,
        version: 0,
      },
      createdAt,
      ...rest,
    };
  });
}

export function initialHistory(job: JobDto): StatusEventDto[] {
  const events: StatusEventDto[] = [
    { fromStatus: null, toStatus: 'SAVED', changedAt: job.createdAt },
  ];
  if (job.application.status !== 'SAVED') {
    events.push({
      fromStatus: 'SAVED',
      toStatus: job.application.status,
      changedAt: job.application.appliedAt ?? job.createdAt,
    });
  }
  return events;
}
