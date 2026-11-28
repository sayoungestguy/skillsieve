import { deleteAccount } from './account';

export default async function globalTeardown(): Promise<void> {
  deleteAccount();
}
