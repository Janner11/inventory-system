import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import styles from '../../styles/layout.module.css';

export default function AppShell() {
  return (
    <div className={styles.appShell}>
      <Navbar />
      <div className={styles.layoutBody}>
        <Sidebar />
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
