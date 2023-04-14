import ReactModal from 'react-modal';
import { useLocalization } from '@fluent/react';
import { BaseModal } from './BaseModal';
import { useForm } from 'react-hook-form';
import { useEffect } from 'react';
import { DeveloperModeWidgetForm } from '../widgets/DeveloperModeWidget';
import { Input } from './Input';

export interface CreateThemeModalForm {
  'background-10': string;
  'background-20': string;
  'background-30': string;
  'background-40': string;
  'background-50': string;
  'background-60': string;
  'background-70': string;
  'background-80': string;
  'background-90': string;

  'accent-background-10': string;
  'accent-background-20': string;
  'accent-background-30': string;
  'accent-background-40': string;
  'accent-background-50': string;

  success: string;
  warning: string;
  critical: string;
  special: string;
  'window-icon-stroke': string;

  'default-color': string;
}

export function CreateThemeModal({
  isOpen = true,
  onClose,
  accept,
  ...props
}: {
  /**
   * Is the parent/sibling component opened?
   */
  isOpen: boolean;
  /**
   * Function to trigger when the neck warning hasn't been accepted
   */
  onClose: () => void;
  /**
   * Function when you press accept
   */
  accept: () => void;
} & ReactModal.Props) {
  const { l10n } = useLocalization();

  const { reset, control, handleSubmit, watch } = useForm<CreateThemeModalForm>(
    {
      defaultValues: {
        'background-10': '#ffffff',
        'background-20': '#78A4C6',
        'background-30': '#608AAB',
        'background-40': '#3D6381',
        'background-50': '#1A3D59',
        'background-60': '#112D43',
        'background-70': '#081E30',
        'background-80': '#00101C',
        'background-90': '#000509',

        'accent-background-10': '#BB8AE5',
        'accent-background-20': '#9D5CD4',
        'accent-background-30': '#65459A',
        'accent-background-40': '#623B83',
        'accent-background-50': '#2E2145',

        success: '#44C27F',
        warning: '#F0DD3A',
        critical: '#DF6D8C',
        special: '#A44FED',
        'window-icon-stroke': '#1C9AC9',

        'default-color': '#1D1D1D',
      },
    }
  );

  useEffect(() => {
    const subscription = watch(() => handleSubmit(onSubmit)());
    return () => subscription.unsubscribe();
  }, []);

  const onSubmit = async (formData: CreateThemeModalForm) => {
    return;
  };

  return (
    <BaseModal
      isOpen={isOpen}
      shouldCloseOnOverlayClick
      shouldCloseOnEsc
      onRequestClose={onClose}
      className={props.className}
      overlayClassName={props.overlayClassName}
    >
      <div className="flex w-full h-full flex-col">
        <form>
          <div className="flex w-full flex-row flex-grow gap-3">
            <div className="flex w-full flex-col flex-grow gap-3">
              <Input
                control={control}
                rules={{ required: true }}
                name="background-10"
                type="color"
                label="Background Color"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-20"
                type="color"
                label="Background Color 20"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-30"
                type="color"
                label="Background Color 30"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-40"
                type="color"
                label="Background Color 40"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-50"
                type="color"
                label="Background Color 50"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-60"
                type="color"
                label="Background Color 60"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-70"
                type="color"
                label="Background Color 70"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-80"
                type="color"
                label="Background Color 80"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="background-90"
                type="color"
                label="Background Color 90"
                variant="untouched"
              />
            </div>
            <div className="flex w-full flex-col flex-grow gap-3">
              <Input
                control={control}
                rules={{ required: true }}
                name="accent-background-10"
                type="color"
                label="Accent Background Color 10"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="accent-background-20"
                type="color"
                label="Accent Background Color 20"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="accent-background-30"
                type="color"
                label="Accent Background Color 30"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="accent-background-40"
                type="color"
                label="Accent Background Color 40"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="accent-background-50"
                type="color"
                label="Accent Background Color 50"
                variant="untouched"
              />
            </div>
            <div className="flex w-full flex-col flex-grow gap-3">
              <Input
                control={control}
                rules={{ required: true }}
                name="success"
                type="color"
                label="Success Color"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="warning"
                type="color"
                label="Warning Color"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="critical"
                type="color"
                label="Critical Color"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="special"
                type="color"
                label="Special Color"
                variant="untouched"
              />
              <Input
                control={control}
                rules={{ required: true }}
                name="default-color"
                type="color"
                label="Default Color"
                variant="untouched"
              />
            </div>
          </div>
        </form>
      </div>
    </BaseModal>
  );
}
