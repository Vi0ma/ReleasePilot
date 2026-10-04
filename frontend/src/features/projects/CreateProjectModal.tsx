import { projectsApi } from '../../api/resources';
import { ResourceForm } from '../../components/ResourceForm';
import { useToast } from '../../components/ToastProvider';
import { navigate } from '../../utils/router';
export function CreateProjectModal({ onClose }: { onClose: () => void }) {
  const notify = useToast();
  return <ResourceForm title="Create project" submitLabel="Create project" onClose={onClose} onSubmit={async input => {
    const project = await projectsApi.create(input);
    notify('Project created with development, staging and production environments.');
    navigate(`/projects/${project.id}`);
  }} />;
}
